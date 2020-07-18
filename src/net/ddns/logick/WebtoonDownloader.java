package net.ddns.logick;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WebtoonDownloader {
    private static final String startToken = "<div class=\"wt_viewer\" style=\"background:#FFFFFF\">";
    private static CloseableHttpClient client;
    private final JFrame frame;
    private final JFileChooser fileChooser;
    private JTextField urlTextField;
    private JTextField fileTextField;
    private JButton browseButton;
    private JButton startButton;
    private JButton cancelButton;
    private JProgressBar progressBar;
    private JPanel mainPanel;
    private boolean cancel = false;
    private Thread thread;

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    public WebtoonDownloader() {
        thread = new Thread(() -> download(urlTextField.getText()));

        fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Куда сохранить?");
        fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSystemView(FileSystemView.getFileSystemView());
        fileChooser.addChoosableFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".png");
            }

            @Override
            public String getDescription() {
                return "PNG file";
            }
        });

        frame = new JFrame("Webtoon Downloader");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setContentPane(mainPanel);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancel = true;
                if (thread.isAlive()) {
                    try {
                        thread.join();
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                } else thread.interrupt();
                frame.dispose();
                System.exit(0);
            }
        });

        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("Помощь");
        JMenuItem menuItem = new JMenuItem("О программе");
        menu.add(menuItem);
        bar.add(menu);
        frame.setJMenuBar(bar);
        menuItem.addActionListener(actionEvent -> {
            JOptionPane.showMessageDialog(null, "WebtoonDownloader\n" +
                    "Программа для скачивания и склейки сканов с корейского вебтуна\nАвтор: MasterLogick\n" +
                    "https://github.com/MasterLogick/WebtoonDownloader");
        });


        cancelButton.addActionListener(e -> new Thread(() -> {
            cancel = true;
            if (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            } else thread.interrupt();
            progressBar.setEnabled(false);
            progressBar.setMaximum(0);
            progressBar.setValue(0);
            progressBar.setString("");
            cancel = false;
        }).start());

        startButton.addActionListener(e -> {
            startButton.setEnabled(false);
            thread = new Thread(() -> {
                download(urlTextField.getText());
                startButton.setEnabled(true);
            });
            thread.start();
        });

        browseButton.addActionListener(e -> {
            if (fileChooser.showDialog(frame, "Сохранить") == JFileChooser.APPROVE_OPTION) {
                fileTextField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });
    }

    public static void main(String[] args) {
        client = HttpClients.custom()
                .setRedirectStrategy(LaxRedirectStrategy.INSTANCE)
                .setUserAgent("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:78.0) Gecko/20100101 Firefox/78.0")
                .setConnectionTimeToLive(20, TimeUnit.SECONDS)
                .build();
                /*.version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();*/

        new WebtoonDownloader().show();
    }

    public void download(String path) {
        progressBar.setEnabled(true);
        progressBar.setString("Скачивание главной страницы");
        progressBar.setMaximum(1);
        progressBar.setValue(0);

        String sb = null;
        try {
            sb = sendRequest(path);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Не удалось скачать главную страницу: " + e.getMessage());
            e.printStackTrace();
        }

        progressBar.setValue(1);

        String data = sb.substring(sb.indexOf(startToken) + startToken.length());
        data = data.substring(0, data.indexOf("</div>"));

        List<String> urls = processData(data);

        progressBar.setMaximum(urls.size());
        progressBar.setValue(0);

        BufferedImage[] images = new BufferedImage[urls.size()];
        int width = 0, height = 0;
        for (int i = 0; i < images.length; i++) {
            if (cancel) return;
            try {
                images[i] = ImageIO.read(client.execute(new HttpGet(urls.get(i))).getEntity().getContent());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Не удалось скачать фрагмент изображения: " + e.getMessage());
                e.printStackTrace();
            }
            progressBar.setString("Скачивание фрагментов " + (i + 1) + " / " + urls.size());
            progressBar.setValue(i + 1);
            if (images[i] == null) {
                JOptionPane.showMessageDialog(null, "Не удалось скачать фрагмент изображения. Скачивание прервано.");
                progressBar.setEnabled(false);
                progressBar.setMaximum(0);
                progressBar.setValue(0);
                progressBar.setString("");
            }
            width = Math.max(images[i].getWidth(), width);
            height += images[i].getHeight();
        }
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = dst.getGraphics();
        int currentHeight = 0;

        progressBar.setValue(0);

        for (int i = 0; i < images.length; i++) {
            if (cancel) return;

            g.drawImage(images[i], 0, currentHeight, null);
            currentHeight += images[i].getHeight();
            progressBar.setString("Склейка " + (i + 1) + " / " + urls.size());
            progressBar.setValue(i + 1);
        }

        progressBar.setString("Сброс на диск");

        try {
            ImageIO.write(dst, "PNG", new File(fileTextField.getText()));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Не удалось сбросить файл на диск: " + e.getLocalizedMessage());
            e.printStackTrace();
        }

        progressBar.setEnabled(false);
        progressBar.setMaximum(0);
        progressBar.setValue(0);
        progressBar.setString("");

        JOptionPane.showMessageDialog(frame, "Страница успешно скачана!");
    }

    public ArrayList<String> processData(String data) {
        ArrayList<String> urls = new ArrayList<>();
        int prefixLength = "<img src=\"".length();
        while (!data.isEmpty()) {
            if (cancel) return null;
            data = data.trim();
            data = data.substring(prefixLength);
            urls.add(data.substring(0, data.indexOf("\"")));
            data = data.substring(data.indexOf(">") + 1);
            data = data.trim();
        }
        return urls;
    }

    public String sendRequest(String uri) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader bf = new BufferedReader(new InputStreamReader(client.execute(new HttpGet(uri)).getEntity().getContent()));
        String s;
        while ((s = bf.readLine()) != null) {
            sb.append(s).append('\n');
        }
        bf.close();
        return sb.toString();
    }

    public void show() {
        frame.pack();
        frame.setVisible(true);
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(2, 2, new Insets(10, 10, 3, 10), -1, -1));
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(panel1, gbc);
        final JLabel label1 = new JLabel();
        label1.setText("Ссылка на главу");
        panel1.add(label1, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        urlTextField = new JTextField();
        panel1.add(urlTextField, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Например, https://comic.naver.com/webtoon/detail.nhn?titleId=318995no=465weekday=fri  ");
        label2.setDisplayedMnemonic('W');
        label2.setDisplayedMnemonicIndex(73);
        panel1.add(label2, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 2, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 3, new Insets(3, 10, 3, 10), -1, -1));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(panel2, gbc);
        final JLabel label3 = new JLabel();
        label3.setText("Сохранить в:");
        panel2.add(label3, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fileTextField = new JTextField();
        panel2.add(fileTextField, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        browseButton = new JButton();
        browseButton.setText("Обзор");
        panel2.add(browseButton, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 3, new Insets(3, 10, 10, 10), -1, -1));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(panel3, gbc);
        startButton = new JButton();
        startButton.setText("Старт");
        panel3.add(startButton, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cancelButton = new JButton();
        cancelButton.setText("Отмена");
        panel3.add(cancelButton, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
        panel3.add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 1, new Insets(3, 10, 3, 10), -1, -1));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(panel4, gbc);
        progressBar = new JProgressBar();
        progressBar.setEnabled(false);
        progressBar.setString("");
        progressBar.setStringPainted(true);
        panel4.add(progressBar, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
