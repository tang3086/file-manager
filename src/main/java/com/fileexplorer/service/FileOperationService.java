package com.fileexplorer.service;

import com.fileexplorer.model.FileItem;
import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FileOperationService {

    // ==================== 文件列表相关方法 ====================

    /**
     * 列出目录下的所有文件和文件夹
     */
    // 在 FileOperationService 中
    public static List<FileItem> listFiles(Path directory) throws IOException {
        List<FileItem> files = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                try {
                    FileItem fileItem = new FileItem(path);

                    // 设置基本属性
                    boolean isDirectory = Files.isDirectory(path);
                    fileItem.setDirectory(isDirectory);

                    if (isDirectory) {
                        // 文件夹
                        fileItem.setType(FileItem.FileType.DIRECTORY);
                        fileItem.setSize(0);
                    } else {
                        // 文件
                        long size = Files.size(path);
                        fileItem.setSize(size);

                        // ==== 使用简化版本判断文件类型 ====
                        String fileName = path.getFileName().toString().toLowerCase();

                        // 判断常见文本文件
                        if (fileName.endsWith(".txt") || fileName.endsWith(".md") ||
                                fileName.endsWith(".java") || fileName.endsWith(".py") ||
                                fileName.endsWith(".cpp") || fileName.endsWith(".c") ||
                                fileName.endsWith(".js") || fileName.endsWith(".html") ||
                                fileName.endsWith(".css") || fileName.endsWith(".xml") ||
                                fileName.endsWith(".json") || fileName.endsWith(".yml") ||
                                fileName.endsWith(".yaml") || fileName.endsWith(".ini") ||
                                fileName.endsWith(".cfg") || fileName.endsWith(".log")) {
                            fileItem.setType(FileItem.FileType.TEXT);
                        }
                        // 判断常见图片文件
                        else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                                fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                                fileName.endsWith(".bmp") || fileName.endsWith(".svg") ||
                                fileName.endsWith(".ico") || fileName.endsWith(".webp")) {
                            fileItem.setType(FileItem.FileType.IMAGE);
                        }
                        // 其他文件设为UNKNOWN
                        else {
                            fileItem.setType(FileItem.FileType.UNKNOWN);
                        }
                    }

                    // 设置修改时间
                    FileTime lastModifiedTime = Files.getLastModifiedTime(path);
                    fileItem.setModifyTime(lastModifiedTime);

                    files.add(fileItem);
                } catch (IOException | SecurityException e) {
                    // 跳过无法访问的文件
                    System.err.println("跳过文件: " + path + " - " + e.getMessage());
                }
            }
        }

        return files;
    }

    /**
     * 创建文件项 - 安全版本
     */
    public static FileItem createFileItem(Path path) {
        try {
            if (path == null || !Files.exists(path)) {
                System.err.println("文件不存在: " + path);
                return null;
            }

            FileItem item = new FileItem(path);
            BasicFileAttributes attrs = null;

            try {
                attrs = Files.readAttributes(path, BasicFileAttributes.class);
            } catch (IOException e) {
                System.err.println("无法读取文件属性: " + path + " - " + e.getMessage());
                // 即使无法读取属性，也返回一个基本的FileItem
                item.setName(path.getFileName().toString());
                item.setDirectory(Files.isDirectory(path));
                item.setType(FileItem.FileType.UNKNOWN);
                return item;
            }

            // 设置基本信息
            item.setName(path.getFileName().toString());
            item.setDirectory(attrs.isDirectory());

            try {
                item.setCreateTime(attrs.creationTime());
            } catch (Exception e) {
                // 创建时间可能不可用
            }

            try {
                item.setModifyTime(attrs.lastModifiedTime());
            } catch (Exception e) {
                // 修改时间可能不可用
            }

            item.setType(determineFileType(path, attrs.isDirectory()));

            if (attrs.isDirectory()) {
                // 计算文件夹内的文件和子文件夹数量
                countDirectoryContents(path, item);
            } else {
                try {
                    item.setSize(attrs.size());
                } catch (Exception e) {
                    item.setSize(0);
                }
            }

            return item;

        } catch (Exception e) {
            System.err.println("创建文件项失败: " + path + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 计算文件夹内容数量 - 安全版本
     */
    private static void countDirectoryContents(Path directory, FileItem item) {
        int fileCount = 0;
        int folderCount = 0;
        long totalSize = 0;

        try {
            if (!Files.exists(directory) || !Files.isDirectory(directory) || !Files.isReadable(directory)) {
                item.setFileCount(0);
                item.setFolderCount(0);
                item.setSize(0);
                return;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path path : stream) {
                    try {
                        if (Files.isDirectory(path)) {
                            folderCount++;
                        } else {
                            fileCount++;
                            try {
                                totalSize += Files.size(path);
                            } catch (IOException e) {
                                // 忽略无法获取大小的文件
                            }
                        }
                    } catch (Exception e) {
                        // 跳过无法处理的文件/文件夹
                    }
                }
            }
        } catch (Exception e) {
            // 如果无法访问目录，设置为0
        }

        item.setFileCount(fileCount);
        item.setFolderCount(folderCount);
        item.setSize(totalSize);
    }

    /**
     * 确定文件类型
     */
    private static FileItem.FileType determineFileType(Path path, boolean isDirectory) {
        if (isDirectory) {
            return FileItem.FileType.DIRECTORY;
        }

        String fileName = path.getFileName().toString().toLowerCase();

        if (fileName.matches(".*\\.(txt|java|cpp|c|h|py|js|html|css|xml|json|md|ini|cfg|conf|log)$")) {
            return FileItem.FileType.TEXT;
        } else if (fileName.matches(".*\\.(jpg|jpeg|png|gif|bmp|svg|webp|ico)$")) {
            return FileItem.FileType.IMAGE;
        } else if (fileName.matches(".*\\.(mp4|avi|mkv|mov|wmv|flv|webm)$")) {
            return FileItem.FileType.VIDEO;
        } else if (fileName.matches(".*\\.(mp3|wav|flac|aac|ogg|wma)$")) {
            return FileItem.FileType.AUDIO;
        } else if (fileName.matches(".*\\.(doc|docx|pdf|ppt|pptx|xls|xlsx)$")) {
            return FileItem.FileType.DOCUMENT;
        } else if (fileName.matches(".*\\.(zip|rar|7z|tar|gz|bz2)$")) {
            return FileItem.FileType.ARCHIVE;
        } else if (fileName.matches(".*\\.(exe|msi|bat|sh|cmd)$")) {
            return FileItem.FileType.EXECUTABLE;
        } else {
            return FileItem.FileType.UNKNOWN;
        }
    }

    // ==================== 带进度报告的文件操作 ====================

    /**
     * 带进度报告的复制单个文件
     */
    public static boolean copyFileWithProgress(Path source, Path target, Consumer<Double> progressCallback) {
        try {
            if (Files.isDirectory(source)) {
                // 对于文件夹，递归复制
                return copyDirectoryWithProgress(source, target, progressCallback);
            } else {
                // 对于文件，使用带进度的复制
                return copySingleFileWithProgress(source, target, progressCallback);
            }
        } catch (IOException e) {
            System.err.println("复制文件失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 复制单个文件，并报告进度
     */
    private static boolean copySingleFileWithProgress(Path source, Path target,
                                                      Consumer<Double> progressCallback) throws IOException {
        long fileSize = Files.size(source);
        System.out.println("复制文件: " + source + " 大小: " + fileSize + " 字节");

        // 创建目标文件的父目录
        Files.createDirectories(target.getParent());

        // 使用带缓冲的流复制文件
        try (var inputStream = Files.newInputStream(source);
             var outputStream = Files.newOutputStream(target)) {

            byte[] buffer = new byte[8192]; // 8KB缓冲区
            long totalCopied = 0;
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalCopied += bytesRead;

                // 计算并报告进度
                double progress = (double) totalCopied / fileSize;
                if (progressCallback != null) {
                    progressCallback.accept(progress);
                }

                // 小延迟，让进度可见
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            // 确保进度达到100%
            if (progressCallback != null) {
                progressCallback.accept(1.0);
            }

            return true;
        }
    }

    /**
     * 复制文件夹，并报告进度
     */
    private static boolean copyDirectoryWithProgress(Path source, Path target,
                                                     Consumer<Double> progressCallback) throws IOException {
        if (!Files.exists(source)) {
            return false;
        }

        // 计算文件夹总大小
        long[] totalSize = {0};
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    totalSize[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            System.err.println("计算文件夹大小时出错: " + e.getMessage());
            totalSize[0] = 0;
        }

        System.out.println("复制文件夹: " + source + " 总大小: " + totalSize[0] + " 字节");

        if (totalSize[0] == 0) {
            // 空文件夹，直接创建
            Files.createDirectories(target);
            if (progressCallback != null) {
                progressCallback.accept(1.0);
            }
            return true;
        }

        final long[] copiedSize = {0};
        final boolean[] success = {true};

        // 遍历文件夹并复制文件
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    // 创建对应的目标目录
                    Path targetDir = target.resolve(source.relativize(dir));
                    Files.createDirectories(targetDir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        Path targetFile = target.resolve(source.relativize(file));

                        // 复制单个文件
                        try (var inputStream = Files.newInputStream(file);
                             var outputStream = Files.newOutputStream(targetFile)) {

                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            long fileSize = attrs.size();
                            long fileCopied = 0;

                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                                fileCopied += bytesRead;
                                copiedSize[0] += bytesRead;

                                // 计算并报告进度
                                double progress = (double) copiedSize[0] / totalSize[0];
                                if (progressCallback != null) {
                                    progressCallback.accept(progress);
                                }

                                // 小延迟
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    success[0] = false;
                                    return FileVisitResult.TERMINATE;
                                }
                            }
                        }

                    } catch (Exception e) {
                        System.err.println("复制文件失败: " + file + " - " + e.getMessage());
                        success[0] = false;
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            System.err.println("遍历文件夹失败: " + e.getMessage());
            success[0] = false;
        }

        return success[0];
    }

    /**
     * 带进度报告的移动文件
     */
    public static boolean moveFileWithProgress(Path source, Path target, Consumer<Double> progressCallback) {
        try {
            // 先尝试直接重命名（在同一磁盘上很快）
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                if (progressCallback != null) {
                    progressCallback.accept(1.0);
                }
                return true;
            } catch (IOException e) {
                // 如果跨磁盘，先复制再删除
                System.out.println("跨磁盘移动，使用复制+删除方式");
                boolean copied = copyFileWithProgress(source, target, progressCallback);
                if (copied) {
                    Files.delete(source);
                    return true;
                }
                return false;
            }
        } catch (IOException e) {
            System.err.println("移动文件失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ==================== 文件大小计算 ====================

    /**
     * 计算文件/文件夹大小
     */
    public static long calculateFileSize(Path path) {
        try {
            if (Files.isDirectory(path)) {
                // 计算文件夹大小
                final long[] size = {0};
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        size[0] += attrs.size();
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        return Files.isReadable(dir) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
                    }
                });
                return size[0];
            } else {
                // 文件大小
                return Files.size(path);
            }
        } catch (IOException e) {
            System.err.println("计算文件大小失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 计算文件列表总大小
     */
    public static long calculateTotalSize(List<Path> paths) {
        long totalSize = 0;
        for (Path path : paths) {
            totalSize += calculateFileSize(path);
        }
        return totalSize;
    }

    // ==================== 基本文件操作 ====================

    /**
     * 复制文件或文件夹
     */
    public static void copy(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            copyDirectory(source, target);
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 复制文件夹
     */
    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 移动文件或文件夹
     */
    public static void move(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 删除文件或文件夹
     */
    public static void delete(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            deleteDirectory(path);
        } else {
            Files.delete(path);
        }
    }

    /**
     * 删除文件夹
     */
    private static void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 重命名文件或文件夹
     */
    public static void rename(Path path, String newName) throws IOException {
        Path target = path.resolveSibling(newName);
        move(path, target);
    }

    /**
     * 创建新文件夹
     */
    public static void createDirectory(Path parentDir, String dirName) throws IOException {
        Path newDir = parentDir.resolve(dirName);
        Files.createDirectories(newDir);
    }

    /**
     * 创建新文件
     */
    public static void createFile(Path parentDir, String fileName) throws IOException {
        Path newFile = parentDir.resolve(fileName);
        Files.createFile(newFile);
    }

    // ==================== 任务版本的文件操作 ====================

    /**
     * 复制文件或文件夹（任务版本）
     */
    public static Task<Void> copyFiles(List<Path> sources, Path targetDir) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                int total = sources.size();
                int completed = 0;

                for (Path source : sources) {
                    if (isCancelled()) {
                        break;
                    }

                    try {
                        updateMessage("正在复制: " + source.getFileName());

                        if (Files.isDirectory(source)) {
                            copyDirectoryRecursive(source, targetDir.resolve(source.getFileName()));
                        } else {
                            Files.copy(source, targetDir.resolve(source.getFileName()),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }

                        completed++;
                        updateProgress(completed, total);

                    } catch (Exception e) {
                        System.err.println("复制失败: " + source + " -> " + e.getMessage());
                    }
                }

                updateMessage("复制完成");
                return null;
            }
        };
    }

    /**
     * 移动文件或文件夹（任务版本）
     */
    public static Task<Void> moveFiles(List<Path> sources, Path targetDir) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                int total = sources.size();
                int completed = 0;

                for (Path source : sources) {
                    if (isCancelled()) {
                        break;
                    }

                    try {
                        updateMessage("正在移动: " + source.getFileName());

                        if (Files.isDirectory(source)) {
                            Files.move(source, targetDir.resolve(source.getFileName()),
                                    StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            Files.move(source, targetDir.resolve(source.getFileName()),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }

                        completed++;
                        updateProgress(completed, total);

                    } catch (Exception e) {
                        System.err.println("移动失败: " + source + " -> " + e.getMessage());
                    }
                }

                updateMessage("移动完成");
                return null;
            }
        };
    }

    /**
     * 递归复制目录
     */
    private static void copyDirectoryRecursive(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    if (!Files.exists(targetPath)) {
                        Files.createDirectory(targetPath);
                    }
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    /**
     * 带进度报告的删除文件/文件夹
     */
    /**
     * 带进度报告的删除文件/文件夹
     */
    public static boolean deleteWithProgress(Path path, Consumer<Double> progressCallback) {
        try {
            if (!Files.exists(path)) {
                return false;
            }

            if (Files.isDirectory(path)) {
                return deleteDirectoryWithProgress(path, progressCallback);
            } else {
                return deleteSingleFileWithProgress(path, progressCallback);
            }
        } catch (Exception e) {
            System.err.println("删除失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 删除单个文件，带进度报告
     */
    private static boolean deleteSingleFileWithProgress(Path path, Consumer<Double> progressCallback) {
        try {
            long fileSize = Files.size(path);
            System.out.println("删除文件: " + path + " 大小: " + fileSize + " 字节");

            // 模拟删除过程（实际删除很快，但为了显示进度）
            for (int i = 0; i < 10; i++) {
                double progress = (i + 1) / 10.0;
                if (progressCallback != null) {
                    progressCallback.accept(progress);
                }
                try {
                    Thread.sleep(50); // 小延迟，让进度可见
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            Files.delete(path);

            if (progressCallback != null) {
                progressCallback.accept(1.0);
            }

            return true;
        } catch (IOException e) {
            System.err.println("删除文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 删除文件夹，带进度报告
     */
    private static boolean deleteDirectoryWithProgress(Path path, Consumer<Double> progressCallback) {
        try {
            // 先计算要删除的文件总数
            final int[] totalCount = {0};
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    totalCount[0]++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(path)) {
                        totalCount[0]++;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            System.out.println("删除文件夹: " + path + " 包含: " + totalCount[0] + " 个文件/文件夹");

            if (totalCount[0] == 0) {
                // 空文件夹
                Files.delete(path);
                if (progressCallback != null) {
                    progressCallback.accept(1.0);
                }
                return true;
            }

            final int[] deletedCount = {0};
            final boolean[] success = {true};

            // 实际删除
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        Files.delete(file);
                        deletedCount[0]++;

                        // 更新进度
                        double progress = (double) deletedCount[0] / totalCount[0];
                        if (progressCallback != null) {
                            progressCallback.accept(progress);
                        }

                        // 小延迟
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            success[0] = false;
                            return FileVisitResult.TERMINATE;
                        }

                    } catch (IOException e) {
                        System.err.println("删除文件失败: " + file + " - " + e.getMessage());
                        success[0] = false;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        if (!dir.equals(path)) {
                            Files.delete(dir);
                            deletedCount[0]++;

                            // 更新进度
                            double progress = (double) deletedCount[0] / totalCount[0];
                            if (progressCallback != null) {
                                progressCallback.accept(progress);
                            }

                            // 小延迟
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                success[0] = false;
                            }
                        }
                    } catch (IOException e) {
                        System.err.println("删除文件夹失败: " + dir + " - " + e.getMessage());
                        success[0] = false;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // 最后删除根文件夹
            if (success[0]) {
                Files.delete(path);
                if (progressCallback != null) {
                    progressCallback.accept(1.0);
                }
            }

            return success[0];
        } catch (IOException e) {
            System.err.println("删除文件夹失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

}  // 类结束