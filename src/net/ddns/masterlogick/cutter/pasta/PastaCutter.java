package net.ddns.masterlogick.cutter.pasta;

import net.ddns.masterlogick.UI.ViewManager;
import net.ddns.masterlogick.cutter.Cutter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class PastaCutter implements Cutter {
    boolean cancel = false;
    int tolerance;
    private final int perfectHeight;
    private final boolean cutOnGradient;
    private static final int MIN_HEIGHT = 30;
    private static final int BORDERS_WIDTH = 10;

    public PastaCutter(int perfectHeight, boolean cutOnGradient, int tolerance) {
        this.perfectHeight = perfectHeight;
        this.cutOnGradient = cutOnGradient;
        this.tolerance = tolerance;
    }

    @Override
    public BufferedImage[] cutScans(BufferedImage[] fragments) {
        return drawFrames(fragments, recognizeFrames(fragments));
    }

    @Override
    public void cancel() {
        cancel = true;
    }

    private List<Frame> recognizeFrames(BufferedImage[] fragments) {
        ViewManager.startProgress(fragments.length, "Рассчёт высот сканов: 0/" + fragments.length);
        ArrayList<Frame> frameInfo = new ArrayList<>();
        boolean scanlineOnWhite = true;
        Frame current = new Frame();
        current.fromY = 0;
        current.fromIndex = 0;
        ImageColorStream ics = new ImageColorStream(fragments[0]);
        for (int x = BORDERS_WIDTH; x < fragments[0].getWidth() - BORDERS_WIDTH; x++) {
            if (!ics.equalsColorsWithEpsilon(0, fragments[0].getWidth() / 2, x, tolerance)) {
                scanlineOnWhite = false;
            }
        }

        int[] prevColor = ics.getColor(fragments[0].getWidth() / 2, 0);

        for (int i = 0; i < fragments.length; i++) {
            ics = new ImageColorStream(fragments[i]);
            x_label:
            for (int y = 0; y < fragments[i].getHeight(); y++) {
                if (cancel) return null;
                int middle = fragments[i].getWidth() / 2;
                if (scanlineOnWhite && !cutOnGradient && !ics.equalsColorsWithEpsilon(middle, y, prevColor, tolerance)) {
                    current.toY = y;
                    current.toIndex = i;
                    current.fixHeight(fragments);
                    if (current.height <= MIN_HEIGHT) {
                        Frame prev = current;
                        if (frameInfo.size() != 0) {
                            prev = frameInfo.remove(frameInfo.size() - 1);
                        }
                        current = prev;
                    } else if (frameInfo.size() != 0) {
                        Frame f = frameInfo.get(frameInfo.size() - 1);
                        Frame frame = current.getFirstHalf(fragments);
                        f.toIndex = frame.toIndex;
                        f.toY = frame.toY;
                        f.fixHeight(fragments);
                        frameInfo.set(frameInfo.size() - 1, f);
                        current = current.getSecondHalf(fragments);
                    }
                    scanlineOnWhite = false;
                    continue;
                }
                for (int x = BORDERS_WIDTH; x < fragments[i].getWidth() - BORDERS_WIDTH; x++) {
                    if (!ics.equalsColorsWithEpsilon(y, middle, x, tolerance)) {
                        if (scanlineOnWhite) {
                            current.toY = y;
                            current.toIndex = i;
                            current.fixHeight(fragments);
                            if (current.height <= MIN_HEIGHT) {
                                Frame prev = current;
                                if (frameInfo.size() != 0) {
                                    prev = frameInfo.remove(frameInfo.size() - 1);
                                }
                                current = prev;
                            } else if (frameInfo.size() != 0) {
                                Frame f = frameInfo.get(frameInfo.size() - 1);
                                Frame frame = current.getFirstHalf(fragments);
                                f.toIndex = frame.toIndex;
                                f.toY = frame.toY;
                                f.fixHeight(fragments);
                                frameInfo.set(frameInfo.size() - 1, f);
                                current = current.getSecondHalf(fragments);
                            }
                            scanlineOnWhite = false;
                        }
                        continue x_label;
                    }
                }

                if (!scanlineOnWhite && !cutOnGradient && !ics.equalsColorsWithEpsilon(middle, y, prevColor, tolerance)) {
                    prevColor = ics.getColor(middle, y);
                    continue;
                }

                prevColor = ics.getColor(middle, y);

                if (i == 0 && y == 0) {//reached only if scanlineOnWhite == true. see first loop in this method
                    continue;
                }
                if (!scanlineOnWhite) {
                    if (y == 0) {
                        current.toIndex = i - 1;
                        current.toY = fragments[current.toIndex].getHeight() - 1;
                    } else {
                        current.toIndex = i;
                        current.toY = y - 1;
                    }
                    current.fixHeight(fragments);
                    frameInfo.add(current);
                    current = new Frame();
                    current.fromIndex = i;
                    current.fromY = y;
                    scanlineOnWhite = true;
                }
            }
            ViewManager.incrementProgress("Рассчёт высот сканов: " + (i + 1) + "/" + fragments.length);
        }

        if (scanlineOnWhite) {
            if (frameInfo.size() != 0) {
                Frame f = frameInfo.get(frameInfo.size() - 1);
                f.toIndex = fragments.length - 1;
                f.toY = fragments[f.toIndex].getHeight() - 1;
                frameInfo.set(frameInfo.size() - 1, f);
            }
        } else {
            current.toIndex = fragments.length - 1;
            current.toY = fragments[current.toIndex].getHeight() - 1;
            frameInfo.add(frameInfo.size() - 1, current);
        }
        return frameInfo;
    }

    private BufferedImage[] drawFrames(BufferedImage[] fragments, List<Frame> frames) {
        if (cancel) return null;
        ViewManager.startProgress(frames.size(), "Склейка сканов: 0/" + frames.size());
        ArrayList<BufferedImage> arr = new ArrayList<>();
        int curHeight = 0;
        int prevEnd = -1;
        for (int i = 0; i < frames.size(); i++) {
            if (cancel) return null;
            if (curHeight + frames.get(i).height < perfectHeight && i != frames.size() - 1) {
                curHeight += frames.get(i).height;
                ViewManager.startProgress(frames.size(), "Склейка сканов: " + (i + 1) + "/" + frames.size());
            } else {
                Frame from = frames.get(prevEnd + 1);
                Frame to;
                if (curHeight > 0 && perfectHeight - curHeight <= frames.get(i).height + curHeight - perfectHeight) {
                    i--;
                } else {
                    ViewManager.startProgress(frames.size(), "Склейка сканов: " + (i + 1) + "/" + frames.size());
                }
                to = frames.get(i);
                prevEnd = i;
                curHeight = 0;
                from.toY = to.toY;
                from.toIndex = to.toIndex;
                from.fixHeight(fragments);
                arr.add(copyImageFromFrame(fragments, from));
            }
        }
        BufferedImage[] buff = new BufferedImage[arr.size()];
        buff = arr.toArray(buff);
        return buff;
    }

    private BufferedImage copyImageFromFrame(BufferedImage[] fragments, Frame frame) {
        int destWidth = 0;
        for (int i = frame.fromIndex; i <= frame.toIndex; i++) {
            destWidth = Math.max(destWidth, fragments[i].getWidth());
        }
        BufferedImage image = new BufferedImage(destWidth, frame.height, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        if (frame.fromIndex == frame.toIndex) {
            g.drawImage(fragments[frame.fromIndex],
                    0, 0,
                    destWidth, frame.height - 1,
                    0, frame.fromY,
                    fragments[frame.fromIndex].getWidth(), frame.toY,
                    null);
        } else {
            int y = 0;
            g.drawImage(fragments[frame.fromIndex],
                    0, 0,
                    destWidth, fragments[frame.fromIndex].getHeight() - frame.fromY,
                    0, frame.fromY,
                    fragments[frame.fromIndex].getWidth(), fragments[frame.fromIndex].getHeight(),
                    null);
            y += fragments[frame.fromIndex].getHeight() - frame.fromY;
            for (int i = frame.fromIndex + 1; i < frame.toIndex; i++) {
                if (cancel) return null;
                g.drawImage(fragments[i],
                        0, y,
                        destWidth, y + fragments[i].getHeight(),
                        0, 0,
                        fragments[i].getWidth(), fragments[i].getHeight(),
                        null);
                y += fragments[i].getHeight();
            }
            g.drawImage(fragments[frame.toIndex],
                    0, y,
                    destWidth, y + frame.toY + 1,
                    0, 0,
                    fragments[frame.toIndex].getWidth(), frame.toY + 1,
                    null);
        }
        return image;
    }
}
