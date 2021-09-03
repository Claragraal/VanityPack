package me.claragraal;

import com.badlogicgames.libimagequant.*;
import org.apache.commons.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;
import java.util.*;

/**
 * @author LBuke (Teddeh)
 */
public final class PackOptimiser {

    public PackOptimiser(File baseDir, File originalResourcePack) {
        File tempPack = new File(baseDir, String.format("temp%s%s", File.separator, originalResourcePack.getName()));
        if (tempPack.exists()) tempPack.delete();
        tempPack.getParentFile().mkdirs();
        try {
            FileUtils.copyDirectory(originalResourcePack, tempPack);
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        TreeMap<Long, File> allFilesMap = new TreeMap<>(Collections.reverseOrder());
        List<File> allFiles = new ArrayList<>();
        long totalSize = 0;
        getAllFiles(allFiles, originalResourcePack, new String[]{""});
        for (File file : allFiles) {
            long length = file.length();
            totalSize += length;
            allFilesMap.put(length, file);
        }

        System.out.printf("Original pack size: %skb%n", (totalSize / 1024));

        // Print out the 10 largest files.
        int i = 0;
        for (Map.Entry<Long, File> entry : allFilesMap.entrySet()) {
            if (i == 10) break;
            System.out.printf("  #%s  %s [%skb]%n", (i + 1), entry.getValue().getAbsolutePath(), (entry.getKey() / 1024));
            i++;
        }

        // optimise image files
        List<File> imageFiles = new ArrayList<>();
        long[] totalImagesLength = {0, 0};
        getAllFiles(imageFiles, tempPack, new String[]{"png"});
        for (File file : imageFiles) {
            totalImagesLength[0] += file.length();
            optimiseImage(file);
            totalImagesLength[1] += new File(file.getAbsolutePath()).length();
        }
        System.out.printf("(%s) Image files: %s [%skb] -> [%skb]%n", originalResourcePack.getName(), imageFiles.size(), (totalImagesLength[0] / 1024), (totalImagesLength[1] / 1024));

        // optimise json files
        List<File> jsonFiles = new ArrayList<>();
        long[] totalJsonLength = {0, 0};
        getAllFiles(jsonFiles, tempPack, new String[]{"json", "png.mcmeta"}, "lang", "font");
        for (File file : jsonFiles) {
            totalJsonLength[0] += file.length();
            optimiseJson(file);
            totalJsonLength[1] += new File(file.getAbsolutePath()).length();
        }
        System.out.printf("(%s) Json files: %s [%skb] -> [%skb]%n", originalResourcePack.getName(), jsonFiles.size(), (totalJsonLength[0] / 1024), (totalJsonLength[1] / 1024));

        // optimise ogg sounds (somehow..)
        List<File> soundFiles = new ArrayList<>();
        long[] totalSoundLength = {0, 0};
        getAllFiles(soundFiles, tempPack, new String[]{"ogg"});
        for (File file : soundFiles) {
            totalSoundLength[0] += file.length();
//            optimiseOgg(file);
            totalSoundLength[1] += new File(file.getAbsolutePath()).length();
        }
        System.out.printf("(%s) Sound files: %s [%skb] -> [%skb]%n", originalResourcePack.getName(), soundFiles.size(), (totalSoundLength[0] / 1024), (totalSoundLength[1] / 1024));

        System.out.println();
    }

    private void getAllFiles(List<File> list, File parent, String[] extensions, String... ignore) {
        File[] children = parent.listFiles();
        if (children == null) return;

        for (File child : children) {
            boolean shouldIgnore = false;
            for (String str : ignore) {
                if (child.getName().equalsIgnoreCase(str)) {
                    shouldIgnore = true;
                    break;
                }
            }
            if (shouldIgnore)
                continue;

            if (child.isDirectory()) {
                this.getAllFiles(list, child, extensions, ignore);
                continue;
            }

            boolean isValidExtension = false;
            for (String type : extensions) {
                if (child.getName().endsWith(type)) {
                    isValidExtension = true;
                    break;
                }
            }

            if (!isValidExtension)
                continue;

            list.add(child);
        }
    }

    private void optimiseJson(File file) {
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
            StringBuilder compressed = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                compressed.append(line.replaceAll("\\s+",""));
            }

            file.delete();
            file.createNewFile();

            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                try (BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(fileOutputStream))) {
                    bufferedWriter.write(compressed.toString());
                }
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private void optimiseImage(File file) {
        new SharedLibraryLoader().load("imagequant-java");
        try {
            BufferedImage input = ImageIO.read(file);
            try {
                byte[] pixels = ((DataBufferByte) input.getRaster().getDataBuffer()).getData();
                for (int i = 0; i < pixels.length; i += 4) {
                    byte a = pixels[i];
                    byte b = pixels[i + 1];
                    byte g = pixels[i + 2];
                    byte r = pixels[i + 3];
                    pixels[i] = r;
                    pixels[i + 1] = g;
                    pixels[i + 2] = b;
                    pixels[i + 3] = a;
                }

                LiqAttribute attribute = new LiqAttribute();
                LiqImage image = new LiqImage(attribute, pixels, input.getWidth(), input.getHeight(), 0);
                LiqResult result = image.quantize();

                int size = input.getWidth() * input.getHeight();
                byte[] quantizedPixels = new byte[size];
                image.remap(result, quantizedPixels);
                LiqPalette palette = result.getPalette();

                BufferedImage convertedImage = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
                byte[] convertedPixels = ((DataBufferByte) convertedImage.getRaster().getDataBuffer()).getData();
                for (int i = 0, j = 0; i < size; i++, j += 4) {
                    int index = quantizedPixels[i] & 0xff;
                    int color = palette.getColor(index);
                    convertedPixels[j] = LiqPalette.getA(color);
                    convertedPixels[j + 1] = LiqPalette.getB(color);
                    convertedPixels[j + 2] = LiqPalette.getG(color);
                    convertedPixels[j + 3] = LiqPalette.getR(color);
                }

                ImageIO.write(convertedImage, "png", file);

                result.destroy();
                image.destroy();
                attribute.destroy();
            } catch (Exception e) {}
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}
