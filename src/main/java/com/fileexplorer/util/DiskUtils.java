// src/main/java/com/fileexplorer/util/DiskUtils.java
package com.fileexplorer.util;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DiskUtils {

    /**
     * 获取所有磁盘驱动器信息
     */
    public static List<DiskInfo> getDiskDrives() {
        List<DiskInfo> disks = new ArrayList<>();

        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            try {
                FileStore store = Files.getFileStore(root);
                DiskInfo info = new DiskInfo(
                        root.toString(),
                        store.name(),
                        store.getTotalSpace(),
                        store.getUsableSpace(),
                        store.getTotalSpace() - store.getUsableSpace()
                );
                disks.add(info);
            } catch (IOException e) {
                System.err.println("无法获取磁盘信息: " + root);
            }
        }

        return disks;
    }

    /**
     * 获取指定路径的磁盘空间信息
     */
    public static DiskInfo getDiskInfo(Path path) throws IOException {
        FileStore store = Files.getFileStore(path);
        return new DiskInfo(
                path.toString(),
                store.name(),
                store.getTotalSpace(),
                store.getUsableSpace(),
                store.getTotalSpace() - store.getUsableSpace()
        );
    }

    public static class DiskInfo {
        private final String path;
        private final String name;
        private final long totalSpace;
        private final long freeSpace;
        private final long usedSpace;

        public DiskInfo(String path, String name, long totalSpace, long freeSpace, long usedSpace) {
            this.path = path;
            this.name = name;
            this.totalSpace = totalSpace;
            this.freeSpace = freeSpace;
            this.usedSpace = usedSpace;
        }

        // Getters
        public String getPath() { return path; }
        public String getName() { return name; }
        public long getTotalSpace() { return totalSpace; }
        public long getFreeSpace() { return freeSpace; }
        public long getUsedSpace() { return usedSpace; }

        /**
         * 获取磁盘使用百分比
         */
        public double getUsagePercentage() {
            return totalSpace == 0 ? 0 : (double) usedSpace / totalSpace * 100;
        }

        /**
         * 格式化大小为可读字符串
         */
        public String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1024));
            String pre = "KMGTPE".charAt(exp-1) + "";
            return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
        }

        @Override
        public String toString() {
            return String.format("%s (%s) 总共: %s, 可用: %s, 已用: %s (%.1f%%)",
                    path, name,
                    formatSize(totalSpace), formatSize(freeSpace), formatSize(usedSpace),
                    getUsagePercentage());
        }
    }
}
