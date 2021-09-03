package me.claragraal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author LBuke (Teddeh)
 */
public final class ResPacker {

    public static void main(String[] args) {
        boolean isHashing = args.length > 0;

        // Get base directory.
        File baseDir = new File(Paths.get("").toAbsolutePath().toString());
        if (!baseDir.exists()) {
            System.out.println("Cannot find base directory");
            System.exit(-1);
            return;
        }

        List<File> resourcePacks = new ArrayList<>();
        File[] baseDirChildren = baseDir.listFiles();
        if (baseDirChildren == null) {
            System.out.println("Something went wrong finding resource packs.");
            System.exit(-1);
            return;
        }

        for (File file : baseDirChildren) {
            if (isResourcePackFolder(file)) {
                resourcePacks.add(file);
            }
        }

        if (resourcePacks.isEmpty()) {
            System.out.println("Could not find any valid resource packs.");
            System.exit(-1);
            return;
        }

        // Create a temp copy of each resource pack, optimise the shit out of them, THEN zip
        resourcePacks.forEach(pack -> new PackOptimiser(baseDir, pack));

        Deque<String> hashes = new LinkedList<>();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("zip.bat");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {//71b2f43c579f88e549a7268a8be3ceaf15140b00
                    if (line.length() == 40) {
                        hashes.addFirst(line);
                    }
                }
            }

            Thread.sleep(10 * 1000);
        } catch (IOException | InterruptedException exception) {
            exception.printStackTrace();
        }

        // Create output text file & zip all resource packs to ./output/___.zip
        File textFile = new File("output/config.txt");
        textFile.getParentFile().mkdirs();
        if (textFile.exists()) textFile.delete();
        try {
            textFile.createNewFile();
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        String newLine = System.getProperty("line.separator");

        try (Writer writer = new BufferedWriter(new FileWriter(textFile))) {
            writer.write("packs:" + newLine);
            writer.write("  game:" + newLine);
            writer.write("    restricted: false" + newLine);
            writer.write("    permission: forceresourcepacks.pack.game" + newLine);
            writer.write("    variants:" + newLine);

            TreeMap<Integer, File> orderedPacks = new TreeMap<>(Collections.reverseOrder());
            for (File pack : resourcePacks) {
                int format = Integer.parseInt(pack.getName().split("_")[0]);
                orderedPacks.put(format, pack);
            }

            for (Map.Entry<Integer, File> entry : orderedPacks.entrySet()) {
                File outputZip = new File(baseDir, String.format("output/%s.zip", entry.getValue().getName()));
                System.out.println(outputZip.getAbsolutePath());
//                String hash = createSha1(outputZip);
                String hash = hashes.pollFirst();

                writer.write("    - url: https://github.com/Claragraal/MythPack/raw/main/output/" + entry.getValue().getName() + ".zip" + newLine);
                writer.write("      hash: " + hash + newLine);
                writer.write("      format: " + entry.getKey() + newLine);
                writer.write("      restricted: false" + newLine);
                writer.write("      permission: forceresourcepacks.pack." + entry.getKey() + newLine);
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * @return [hash, format]
     */
    private static String[] createZip(File pack) {
        String[] array = new String[2];

        String outputName = pack.getName().split("_")[0];
        File output = new File(pack.getParent(), String.format("output%s%s.zip", File.separator, outputName));
        output.getParentFile().mkdirs();
        if (output.exists()) output.delete();

        try (FileOutputStream fileOutputStream = new FileOutputStream(output)) {
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream, StandardCharsets.UTF_8)) {
                List<File> childrenFiles = new ArrayList<>();
                getChildrenFiles(pack, childrenFiles);

                // Loop through all inner directories
                for (File file : childrenFiles) {
                    String filePath = file.getCanonicalPath();
                    int packPathLength = pack.getCanonicalPath().length();
                    int filePathLength = filePath.length();

                    String zipFilePath = filePath.substring(packPathLength + 1, filePathLength);
                    ZipEntry zipEntry = new ZipEntry(zipFilePath);
                    zipOutputStream.putNextEntry(zipEntry);

                    try (FileInputStream fileInputStream = new FileInputStream(file)) {
                        byte[] bytes = new byte[1024];
                        int length;
                        while ((length = fileInputStream.read(bytes)) >= 0) {
                            zipOutputStream.write(bytes, 0, length);
                        }
                        zipOutputStream.closeEntry();
                        fileInputStream.close();
                    }
                }

                zipOutputStream.finish();
                zipOutputStream.close();
                fileOutputStream.close();

                System.out.printf("Finished zipping: %s%n", output.getName());
                String hash = createSha1(output);
                System.out.printf("Hash: %s%n", hash);
                System.out.println();

                array[0] = hash;
                array[1] = outputName;
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        return array;
    }

    private static String createSha1(File file) {
        byte[] bytes = new byte[0];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream inputStream = new FileInputStream(file)) {
                int n = 0;
                byte[] buffer = new byte[8192];
                while (n != -1) {
                    n = inputStream.read(buffer);
                    if (n > 0) {
                        digest.update(buffer, 0, n);
                    }
                }
            } catch (IOException exception) {
                exception.printStackTrace();
            }
            bytes = digest.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return byteArrayToHexString(bytes);
    }

    private static String byteArrayToHexString(byte[] array) {
        StringBuilder result = new StringBuilder();
        for (byte b : array) {
            result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }

    private static void getChildrenFiles(File file, List<File> list) {
        File[] children = file.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (child.isDirectory()) {
                getChildrenFiles(child, list);
                continue;
            }

            list.add(child);
        }
    }

    private static boolean isResourcePackFolder(File file) {
        if (!file.isDirectory()) return false;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && child.getName().equals("assets")) {
                    return true;
                }
            }
        }
        return false;
    }
}
