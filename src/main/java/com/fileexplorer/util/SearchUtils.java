package com.fileexplorer.util;

import com.fileexplorer.model.FileItem;
import com.fileexplorer.service.FileOperationService;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SearchUtils {

    /**
     * 按文件名搜索（支持通配符）
     */
    public static List<FileItem> searchByName(Path startDir, String pattern) throws IOException {
        List<FileItem> results = new ArrayList<>();

        if (!Files.exists(startDir) || !Files.isDirectory(startDir)) {
            System.err.println("搜索路径不存在或不是文件夹: " + startDir);
            return results;
        }

        String regex = convertWildcardToRegex(pattern);
        Pattern compiledPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);

        System.out.println("搜索正则表达式: " + regex);

        Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    Path fileName = file.getFileName();
                    if (fileName != null) {
                        String fileNameStr = fileName.toString();
                        if (compiledPattern.matcher(fileNameStr).matches()) {
                            FileItem item = FileOperationService.createFileItem(file);
                            if (item != null) {
                                results.add(item);
                            }
                        }
                    }
                } catch (Exception e) {
                    // 跳过无法访问的文件
                    System.err.println("跳过文件: " + file + ", 错误: " + e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                try {
                    // 跳过起始目录本身
                    if (dir.equals(startDir)) {
                        return FileVisitResult.CONTINUE;
                    }

                    Path dirName = dir.getFileName();
                    if (dirName != null) {
                        String dirNameStr = dirName.toString();
                        if (compiledPattern.matcher(dirNameStr).matches()) {
                            FileItem item = FileOperationService.createFileItem(dir);
                            if (item != null) {
                                results.add(item);
                            }
                        }
                    }
                } catch (Exception e) {
                    // 跳过无法访问的目录
                    System.err.println("跳过目录: " + dir + ", 错误: " + e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                // 跳过无法访问的文件
                System.err.println("无法访问文件: " + file + ", 原因: " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    System.err.println("访问目录后出错: " + dir + ", 原因: " + exc.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return results;
    }

    /**
     * 搜索大文件
     */
    public static List<FileItem> searchLargeFiles(Path startDir, long minSize) throws IOException {
        List<FileItem> results = new ArrayList<>();

        if (!Files.exists(startDir) || !Files.isDirectory(startDir)) {
            return results;
        }

        Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    if (attrs.size() >= minSize) {
                        FileItem item = FileOperationService.createFileItem(file);
                        if (item != null) {
                            results.add(item);
                        }
                    }
                } catch (Exception e) {
                    // 跳过无法访问的文件
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return results;
    }

    /**
     * 按文件类型搜索
     */
    public static List<FileItem> searchByType(Path startDir, FileItem.FileType type) throws IOException {
        List<FileItem> results = new ArrayList<>();

        if (!Files.exists(startDir) || !Files.isDirectory(startDir)) {
            return results;
        }

        Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    FileItem item = FileOperationService.createFileItem(file);
                    if (item != null && item.getType() == type) {
                        results.add(item);
                    }
                } catch (Exception e) {
                    // 跳过无法访问的文件
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (type == FileItem.FileType.DIRECTORY) {
                    try {
                        FileItem item = FileOperationService.createFileItem(dir);
                        if (item != null) {
                            results.add(item);
                        }
                    } catch (Exception e) {
                        // 跳过无法访问的目录
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return results;
    }

    /**
     * 将通配符模式转换为正则表达式
     */
    private static String convertWildcardToRegex(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return ".*";  // 匹配所有
        }

        pattern = pattern.trim();

        // 如果模式以点开头且不包含星号，自动添加星号
        if (pattern.startsWith(".") && !pattern.contains("*")) {
            pattern = "*" + pattern;
        }

        // 如果模式不包含通配符，自动添加通配符
        if (!pattern.contains("*") && !pattern.contains("?") && !pattern.contains(".")) {
            pattern = "*" + pattern + "*";
        }

        // 如果模式以 * 开头，确保只有一个
        if (pattern.startsWith("*")) {
            pattern = pattern.replaceAll("^\\*+", "*");
        }

        // 如果模式以 * 结尾，确保只有一个
        if (pattern.endsWith("*")) {
            pattern = pattern.replaceAll("\\*+$", "*");
        }

        StringBuilder regex = new StringBuilder();

        // 添加行起始锚点
        regex.append("^");

        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
                    break;
                case '.':
                    regex.append("\\.");
                    break;
                default:
                    // 特殊字符转义
                    if (Character.isLetterOrDigit(c)) {
                        regex.append(c);
                    } else {
                        regex.append("\\").append(c);
                    }
                    break;
            }
        }

        // 添加行结束锚点
        regex.append("$");

        return regex.toString();
    }
}