package net.macu.UI;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import net.macu.core.Pipeline;
import net.macu.cutter.SingleScanCutter;
import net.macu.disk.SingleScanSaver;

import javax.swing.*;
import java.awt.*;

public class SinglePageForm implements Form {
    private JTextField filePathTextField;
    private JButton browseButton;
    public JPanel form;

    public SinglePageForm() {
        browseButton.addActionListener(e -> {
            String path = ViewManager.requestSelectSingleFile("Сохранить");
            filePathTextField.setText(path);
        });
    }

    @Override
    public boolean validateInput() {
        String path = filePathTextField.getText();
        if (path.isEmpty()) {
            ViewManager.showMessageDialog("Не указан путь для сохранения");
            return false;
        }
        if (!path.endsWith(".png")) {
            return ViewManager.showConfirmDialog("Путь указан без расширения .png; Надеюсь, Вы знаете, что делаете");
        }
        return true;
    }

    @Override
    public JComponent getRootComponent() {
        return $$$getRootComponent$$$();
    }

    public String getDescription() {
        return "Склеить всё в один длинный скан";
    }

    @Override
    public Pipeline getConfiguredPipeline() {
        return new Pipeline(new SingleScanCutter(), new SingleScanSaver(filePathTextField.getText()));
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        form = new JPanel();
        form.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        final JLabel label1 = new JLabel();
        label1.setText("Сохранить как:");
        form.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        filePathTextField = new JTextField();
        form.add(filePathTextField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        browseButton = new JButton();
        browseButton.setText("Обзор");
        form.add(browseButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return form;
    }

}