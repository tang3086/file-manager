package com.fileexplorer.model;

import javafx.beans.property.*;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FileItem {
    // 原有属性保持不变
    private StringProperty name = new SimpleStringProperty();
    private LongProperty size = new SimpleLongProperty();
    private ObjectProperty<FileType> type = new SimpleObjectProperty<>();
    private ObjectProperty<LocalDateTime> modifyTime = new SimpleObjectProperty<>();
    private BooleanProperty directory = new SimpleBooleanProperty();
    private IntegerProperty fileCount = new SimpleIntegerProperty();
    private IntegerProperty folderCount = new SimpleIntegerProperty();
    private Path path; // 保留原始Path对象

    // 新增内部优化属性
    private StringProperty displayName = new SimpleStringProperty();
    private StringProperty displaySize = new SimpleStringProperty();
    private StringProperty displayType = new SimpleStringProperty();
    private StringProperty displayTime = new SimpleStringProperty();
    private StringProperty extension = new SimpleStringProperty();

    // 时间格式化器
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public enum FileType {
        DIRECTORY("文件夹"),
        TEXT("文本文件"),
        IMAGE("图片"),
        VIDEO("视频"),
        AUDIO("音频"),
        DOCUMENT("文档"),
        ARCHIVE("压缩包"),
        EXECUTABLE("可执行文件"),
        UNKNOWN("未知类型");

        private final String displayName;

        FileType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public FileItem(Path path) {
        this.path = path;
        String fileName = path.getFileName() != null ?
                path.getFileName().toString() :
                path.toString();
        setName(fileName);
        updateExtension();
    }

    // Property getters - 原有保持不变
    public StringProperty nameProperty() { return name; }
    public LongProperty sizeProperty() { return size; }
    public ObjectProperty<FileType> typeProperty() { return type; }
    public ObjectProperty<LocalDateTime> modifyTimeProperty() { return modifyTime; }
    public BooleanProperty directoryProperty() { return directory; }
    public IntegerProperty fileCountProperty() { return fileCount; }
    public IntegerProperty folderCountProperty() { return folderCount; }

    // 新增属性getters
    public StringProperty displayNameProperty() { return displayName; }
    public StringProperty displaySizeProperty() { return displaySize; }
    public StringProperty displayTypeProperty() { return displayType; }
    public StringProperty displayTimeProperty() { return displayTime; }
    public StringProperty extensionProperty() { return extension; }

    // 普通getter方法 - 原有保持不变
    public String getName() { return name.get(); }
    public Path getPath() { return path; }
    public long getSize() { return size.get(); }
    public FileType getType() { return type.get(); }
    public LocalDateTime getModifyTime() { return modifyTime.get(); }
    public boolean isDirectory() { return directory.get(); }
    public int getFileCount() { return fileCount.get(); }
    public int getFolderCount() { return folderCount.get(); }

    // 新增getter方法
    public String getDisplayName() { return displayName.get(); }
    public String getDisplaySize() { return displaySize.get(); }
    public String getDisplayType() { return displayType.get(); }
    public String getDisplayTime() { return displayTime.get(); }
    public String getExtension() { return extension.get(); }

    // 普通setter方法 - 修改内部实现
    public void setName(String name) {
        this.name.set(name);
        this.displayName.set(name);
        updateExtension();
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public void setSize(long size) {
        this.size.set(size);
        updateDisplaySize();
    }

    public void setType(FileType type) {
        this.type.set(type);
        this.displayType.set(type.getDisplayName());
    }

    public void setDirectory(boolean directory) {
        this.directory.set(directory);
        if (directory) {
            setType(FileType.DIRECTORY);
        } else {
            detectFileType();
        }
        updateDisplaySize();
    }

    public void setFileCount(int fileCount) {
        this.fileCount.set(fileCount);
        updateDisplaySize();
    }

    public void setFolderCount(int folderCount) {
        this.folderCount.set(folderCount);
        updateDisplaySize();
    }

    // 特殊setter方法 - 修改内部实现
    public void setModifyTime(LocalDateTime modifyTime) {
        this.modifyTime.set(modifyTime);
        if (modifyTime != null) {
            this.displayTime.set(modifyTime.format(TIME_FORMATTER));
        } else {
            this.displayTime.set("");
        }
    }

    public void setModifyTime(FileTime fileTime) {
        if (fileTime != null) {
            LocalDateTime time = LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.systemDefault());
            setModifyTime(time);
        } else {
            setModifyTime((LocalDateTime) null);
        }
    }

    public void setCreateTime(LocalDateTime createTime) {
        // 暂时不存储创建时间
    }

    public void setCreateTime(FileTime fileTime) {
        // 暂时不存储创建时间
    }

    // 新增内部方法
    /**
     * 更新文件扩展名
     */
    private void updateExtension() {
        String fileName = getName();
        if (fileName == null) {
            extension.set("");
            return;
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            String ext = fileName.substring(dotIndex).toLowerCase();
            extension.set(ext);
        } else {
            extension.set("");
        }
    }

    /**
     * 更新显示大小
     */
    private void updateDisplaySize() {
        if (isDirectory()) {
            int files = getFileCount();
            int folders = getFolderCount();
            if (files == 0 && folders == 0) {
                displaySize.set("空文件夹");
            } else {
                displaySize.set(String.format("%d 文件, %d 文件夹", files, folders));
            }
        } else {
            long size = getSize();
            displaySize.set(formatFileSize(size));
        }
    }

    /**
     * 格式化文件大小显示
     */
    private String formatFileSize(long size) {
        if (size < 0) {
            return "未知大小";
        }
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 根据文件名检测文件类型
     */
    private void detectFileType() {
        String fileName = getName().toLowerCase();

        if (fileName.endsWith(".txt") || fileName.endsWith(".md") ||
                fileName.endsWith(".rtf") || fileName.endsWith(".log")) {
            setType(FileType.TEXT);
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                fileName.endsWith(".bmp") || fileName.endsWith(".svg")) {
            setType(FileType.IMAGE);
        } else if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") ||
                fileName.endsWith(".mov") || fileName.endsWith(".wmv") ||
                fileName.endsWith(".flv") || fileName.endsWith(".mkv")) {
            setType(FileType.VIDEO);
        } else if (fileName.endsWith(".mp3") || fileName.endsWith(".wav") ||
                fileName.endsWith(".flac") || fileName.endsWith(".aac") ||
                fileName.endsWith(".ogg") || fileName.endsWith(".m4a")) {
            setType(FileType.AUDIO);
        } else if (fileName.endsWith(".pdf") || fileName.endsWith(".doc") ||
                fileName.endsWith(".docx") || fileName.endsWith(".xls") ||
                fileName.endsWith(".xlsx") || fileName.endsWith(".ppt") ||
                fileName.endsWith(".pptx")) {
            setType(FileType.DOCUMENT);
        } else if (fileName.endsWith(".zip") || fileName.endsWith(".rar") ||
                fileName.endsWith(".7z") || fileName.endsWith(".tar") ||
                fileName.endsWith(".gz") || fileName.endsWith(".bz2")) {
            setType(FileType.ARCHIVE);
        } else if (fileName.endsWith(".exe") || fileName.endsWith(".bat") ||
                fileName.endsWith(".cmd") || fileName.endsWith(".sh") ||
                fileName.endsWith(".msi")) {
            setType(FileType.EXECUTABLE);
        } else {
            setType(FileType.UNKNOWN);
        }
    }

    /**
     * 获取无点的扩展名
     */
    public String getExtensionWithoutDot() {
        String ext = getExtension();
        if (ext != null && ext.startsWith(".") && ext.length() > 1) {
            return ext.substring(1);
        }
        return ext != null ? ext : "";
    }

    /**
     * 检查是否是图片文件
     */
    public boolean isImageFile() {
        return getType() == FileType.IMAGE;
    }

    /**
     * 检查是否是文档文件
     */
    public boolean isDocumentFile() {
        return getType() == FileType.DOCUMENT;
    }

    /**
     * 检查是否是视频文件
     */
    public boolean isVideoFile() {
        return getType() == FileType.VIDEO;
    }

    /**
     * 检查是否是音频文件
     */
    public boolean isAudioFile() {
        return getType() == FileType.AUDIO;
    }

    /**
     * 检查是否是压缩文件
     */
    public boolean isArchiveFile() {
        return getType() == FileType.ARCHIVE;
    }

    /**
     * 检查是否是文本文件
     */
    public boolean isTextFile() {
        return getType() == FileType.TEXT;
    }

    /**
     * 检查是否是可执行文件
     */
    public boolean isExecutableFile() {
        return getType() == FileType.EXECUTABLE;
    }

    @Override
    public String toString() {
        return getName();
    }
}
