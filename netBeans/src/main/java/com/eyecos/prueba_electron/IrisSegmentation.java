package com.eyecos.prueba_electron;

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;

public class IrisSegmentation {

    private static final int RADIUS_SEARCH_STEP = 1;
    private static final int POINT_SEARCH_STEP = 1;
    private static final int ANGULAR_SAMPLES = 72; // 5-degree intervals

    private static final double[] COS_TABLE = new double[ANGULAR_SAMPLES];
    private static final double[] SIN_TABLE = new double[ANGULAR_SAMPLES];

    static {
        for (int i = 0; i < ANGULAR_SAMPLES; i++) {
            double angle = 2 * Math.PI * i / ANGULAR_SAMPLES;
            COS_TABLE[i] = Math.cos(angle);
            SIN_TABLE[i] = Math.sin(angle);
        }
    }

    public static class IrisData {
        public int pupilCenterX;
        public int pupilCenterY;
        public int pupilRadius;
        public int irisCenterX;
        public int irisCenterY;
        public int irisRadius;

        public IrisData(int pupilCenterX, int pupilCenterY, int pupilRadius,
                        int irisCenterX, int irisCenterY, int irisRadius) {
            this.pupilCenterX = pupilCenterX;
            this.pupilCenterY = pupilCenterY;
            this.pupilRadius = pupilRadius;
            this.irisCenterX = irisCenterX;
            this.irisCenterY = irisCenterY;
            this.irisRadius = irisRadius;
        }
    }

    public static IrisData segmentIris(BufferedImage inputImage, int resolution) {
        int originalResolution = inputImage.getWidth();

        BufferedImage scaledImage = resizeToSquare(inputImage, resolution);

        byte[] grayPixels = convertToGrayscale(scaledImage);
        byte[] blurredPixels = applyGaussianBlur(grayPixels);

        int[] pupilData = findPupil(blurredPixels);
        int[] irisData = findIris(blurredPixels);

        float scale = (float)originalResolution / resolution;

        return new IrisData(
                Math.round(pupilData[0] * scale),
                Math.round(pupilData[1] * scale),
                Math.round(pupilData[2] * scale),
                Math.round(irisData[0] * scale),
                Math.round(irisData[1] * scale),
                Math.round(irisData[2] * scale)
        );
    }

    public static BufferedImage resizeToSquare(BufferedImage original, int size) {
        int width = original.getWidth();
        int height = original.getHeight();
        float scale = (float) size / Math.max(width, height);
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);

        Image tmp = original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        BufferedImage squareImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = squareImage.createGraphics();
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, size, size);
        int x = (size - newWidth) / 2;
        int y = (size - newHeight) / 2;
        g.drawImage(scaledImage, x, y, null);
        g.dispose();

        return squareImage;
    }

    
    public static byte[] convertToGrayscale(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        byte[] grayscaleBytes = new byte[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = new Color(original.getRGB(x, y));
                int gray = (int) (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());
                grayscaleBytes[y * width + x] = (byte) gray;
            }
        }

        return grayscaleBytes;
    }

    private static byte[] applyGaussianBlur(byte[] pixels) {
        int width = (int) Math.sqrt(pixels.length);
        byte[] result = new byte[width * width];

        final float[] KERNEL = {
                0.003f, 0.013f, 0.022f, 0.013f, 0.003f,
                0.013f, 0.059f, 0.097f, 0.059f, 0.013f,
                0.022f, 0.097f, 0.159f, 0.097f, 0.022f,
                0.013f, 0.059f, 0.097f, 0.059f, 0.013f,
                0.003f, 0.013f, 0.022f, 0.013f, 0.003f
        };

        int kernelRadius = 2;

        for (int y = kernelRadius; y < width - kernelRadius; y++) {
            for (int x = kernelRadius; x < width - kernelRadius; x++) {
                float sum = 0;

                for (int ky = -kernelRadius; ky <= kernelRadius; ky++) {
                    for (int kx = -kernelRadius; kx <= kernelRadius; kx++) {
                        int pixelValue = pixels[(y + ky) * width + (x + kx)] & 0xFF;
                        sum += pixelValue * KERNEL[(ky + kernelRadius) * 5 + (kx + kernelRadius)];
                    }
                }

                result[y * width + x] = (byte)Math.min(255, Math.max(0, Math.round(sum)));
            }
        }

        for (int y = 0; y < kernelRadius; y++) {
            System.arraycopy(pixels, y * width, result, y * width, width);
            System.arraycopy(pixels, (width - y - 1) * width,
                    result, (width - y - 1) * width, width);
        }

        for (int y = kernelRadius; y < width - kernelRadius; y++) {
            for (int x = 0; x < kernelRadius; x++) {
                result[y * width + x] = pixels[y * width + x];
                result[y * width + (width - x - 1)] = pixels[y * width + (width - x - 1)];
            }
        }

        return result;
    }

    private static int[] findPupil(byte[] pixels) {
        int minRadius = (int) Math.sqrt(pixels.length) / 10;
        int maxRadius = (int) Math.sqrt(pixels.length) / 6;

        return daugmanOperator(pixels, minRadius, maxRadius);
    }

    private static int[] findIris(byte[] pixels) {
        int minRadius = (int) Math.sqrt(pixels.length) / 10;
        int maxRadius = (int) Math.sqrt(pixels.length) / 4;

        return daugmanOperator(pixels, minRadius, maxRadius);
    }

    private static int[] daugmanOperator(byte[] pixels, int minRadius, int maxRadius) {
        int width = (int) Math.sqrt(pixels.length);
        int center = width/2;
        int bestCenterX = center;
        int bestCenterY = center;
        int bestRadius = minRadius;
        double maxScore = -1;

        int searchRadius = width/4;

        for (int cy = center - searchRadius; cy <= center + searchRadius; cy += POINT_SEARCH_STEP) {
            for (int cx = center - searchRadius; cx <= center + searchRadius; cx += POINT_SEARCH_STEP) {
                for (int r = minRadius; r <= maxRadius; r += RADIUS_SEARCH_STEP) {
                    double score = calculateDaugmanScore(pixels, cx, cy, r);

                    if (score > maxScore) {
                        maxScore = score;
                        bestCenterX = cx;
                        bestCenterY = cy;
                        bestRadius = r;
                    }
                }
            }
        }

        return new int[] { bestCenterX, bestCenterY, bestRadius };
    }

    private static double calculateDaugmanScore(byte[] pixels,
                                                int centerX, int centerY, int radius) {
        int width = (int) Math.sqrt(pixels.length);
        double score = 0;
        int validPoints = 0;

        for (int i = 0; i < ANGULAR_SAMPLES; i++) {
            int x = (int) (centerX + radius * COS_TABLE[i]);
            int y = (int) (centerY + radius * SIN_TABLE[i]);

            if (x < 2 || x >= width - 2 || y < 2 || y >= width - 2) {
                continue;
            }

            double gradient = calculateGradient(pixels, x, y);

            if (gradient > 0){
                score += gradient;
                validPoints++;
            }
        }

        if (validPoints > 0) {
            score /= validPoints;

            double coverageRatio = (double) validPoints / ANGULAR_SAMPLES;
            if (coverageRatio < 0.75) {
                score *= coverageRatio;
            }
        } else {
            return 0;
        }

        return score;
    }

    private static double calculateGradient(byte[] pixels, int x, int y) {
        int width = (int) Math.sqrt(pixels.length);
        int p00 = pixels[(y-1) * width + (x-1)] & 0xFF;
        int p01 = pixels[(y-1) * width + x] & 0xFF;
        int p02 = pixels[(y-1) * width + (x+1)] & 0xFF;
        int p10 = pixels[y * width + (x-1)] & 0xFF;
        int p12 = pixels[y * width + (x+1)] & 0xFF;
        int p20 = pixels[(y+1) * width + (x-1)] & 0xFF;
        int p21 = pixels[(y+1) * width + x] & 0xFF;
        int p22 = pixels[(y+1) * width + (x+1)] & 0xFF;

        int gx = -p00 + p02 - 2*p10 + 2*p12 - p20 + p22;
        int gy = -p00 - 2*p01 - p02 + p20 + 2*p21 + p22;

        return Math.sqrt(gx * gx + gy * gy);
    }

    public static IrisData segmentIris(BufferedImage inputImage) {
        return segmentIris(inputImage, 256);
    }

    public static BufferedImage drawSegmentation(BufferedImage original, IrisData data) {
        int width  = original.getWidth();
        int height = original.getHeight();

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();

        int[] pixels = result.getRGB(0, 0, width, height, null, 0, width);

        drawCircle(pixels, width, height,
                   data.pupilCenterX, data.pupilCenterY,
                   data.pupilRadius, Color.GREEN.getRGB());
        drawCircle(pixels, width, height,
                   data.irisCenterX, data.irisCenterY,
                   data.irisRadius, Color.RED.getRGB());

        result.setRGB(0, 0, width, height, pixels, 0, width);

        return result;
    }

    private static void drawCircle(int[] pixels, int width, int height,
                                   int centerX, int centerY, int radius, int color) {
        drawSingleCircle(pixels, width, height, centerX, centerY, radius - 1, color);
        drawSingleCircle(pixels, width, height, centerX, centerY, radius, color);
        drawSingleCircle(pixels, width, height, centerX, centerY, radius + 1, color);
    }

    private static void drawSingleCircle(int[] pixels, int width, int height,
                                         int centerX, int centerY, int radius, int color) {
        int x = 0;
        int y = radius;
        int d = 3 - 2 * radius;

        while (y >= x) {
            setPixelSafe(pixels, width, height, centerX + x, centerY + y, color);
            setPixelSafe(pixels, width, height, centerX + y, centerY + x, color);
            setPixelSafe(pixels, width, height, centerX - y, centerY + x, color);
            setPixelSafe(pixels, width, height, centerX - x, centerY + y, color);
            setPixelSafe(pixels, width, height, centerX - x, centerY - y, color);
            setPixelSafe(pixels, width, height, centerX - y, centerY - x, color);
            setPixelSafe(pixels, width, height, centerX + y, centerY - x, color);
            setPixelSafe(pixels, width, height, centerX + x, centerY - y, color);

            if (d < 0) {
                d = d + 4 * x + 6;
            } else {
                d = d + 4 * (x - y) + 10;
                y--;
            }
            x++;
        }
    }

    private static void setPixelSafe(int[] pixels, int width, int height, int x, int y, int color) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            pixels[y * width + x] = color;
        }
    }
}