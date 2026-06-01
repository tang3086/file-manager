package com.fileexplorer.util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.HashMap;
import java.util.Map;

public class IconUtils {
    private static final Map<String, String> iconMap = new HashMap<>();

    static {
        // 文件夹图标
        iconMap.put("folder", "/com/fileexplorer/icons/folder.png");
        iconMap.put("folder-open", "/com/fileexplorer/icons/folder_open.png");

        // 文件类型图标
        iconMap.put("text", "/com/fileexplorer/icons/text.png");
        iconMap.put("image", "/com/fileexplorer/icons/image.png");
        iconMap.put("audio", "/com/fileexplorer/icons/audio.png");
        iconMap.put("video", "/com/fileexplorer/icons/video.png");
        iconMap.put("pdf", "/com/fileexplorer/icons/pdf.png");
        iconMap.put("zip", "/com/fileexplorer/icons/zip.png");
        iconMap.put("exe", "/com/fileexplorer/icons/exe.png");
        iconMap.put("unknown", "/com/fileexplorer/icons/unknown.png");
    }

    /**
     * 根据文件名获取图标
     */
    public static ImageView getIconForFile(String fileName, boolean isDirectory) {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(16);
        imageView.setFitHeight(16);

        if (isDirectory) {
            // 使用文件夹图标
            javafx.scene.shape.Rectangle folderIcon = new javafx.scene.shape.Rectangle(12, 10);
            folderIcon.setFill(javafx.scene.paint.Color.LIGHTBLUE);
            // 这里只是示例，实际可以创建更复杂的图形
        }

        return imageView;
    }

    /**
     * 获取图标的路径
     */
    private static String getIconPathForFile(String fileName, boolean isDirectory) {
        if (isDirectory) {
            return iconMap.get("folder");
        }

        String extension = getFileExtension(fileName).toLowerCase();

        // 文本文件
        if (extension.equals("txt") || extension.equals("md") || extension.equals("ini") ||
                extension.equals("cfg") || extension.equals("conf") || extension.equals("log")) {
            return iconMap.get("text");
        }

        // 图片文件
        if (extension.equals("jpg") || extension.equals("jpeg") || extension.equals("png") ||
                extension.equals("gif") || extension.equals("bmp") || extension.equals("ico")) {
            return iconMap.get("image");
        }

        // 音频文件
        if (extension.equals("mp3") || extension.equals("wav") || extension.equals("flac") ||
                extension.equals("aac") || extension.equals("ogg")) {
            return iconMap.get("audio");
        }

        // 视频文件
        if (extension.equals("mp4") || extension.equals("avi") || extension.equals("mkv") ||
                extension.equals("mov") || extension.equals("wmv")) {
            return iconMap.get("video");
        }

        // PDF文件
        if (extension.equals("pdf")) {
            return iconMap.get("pdf");
        }

        // 压缩文件
        if (extension.equals("zip") || extension.equals("rar") || extension.equals("7z") ||
                extension.equals("tar") || extension.equals("gz")) {
            return iconMap.get("zip");
        }

        // 可执行文件
        if (extension.equals("exe") || extension.equals("bat") || extension.equals("sh") ||
                extension.equals("cmd")) {
            return iconMap.get("exe");
        }

        // 未知文件类型
        return iconMap.get("unknown");
    }

    /**
     * 获取文件扩展名
     */
    private static String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1);
    }
}
