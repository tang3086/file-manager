// src/main/java/com/fileexplorer/util/SortUtils.java
package com.fileexplorer.util;

import com.fileexplorer.model.FileItem;

import java.util.Comparator;
import java.util.List;

public class SortUtils {

    public enum SortBy {
        NAME, SIZE, TYPE, DATE_CREATED, DATE_MODIFIED
    }

    public enum SortOrder {
        ASCENDING, DESCENDING
    }

    /**
     * 对文件列表进行排序
     */
    public static void sortFiles(List<FileItem> files, SortBy sortBy, SortOrder order) {
        Comparator<FileItem> comparator = getComparator(sortBy);

        if (order == SortOrder.DESCENDING) {
            comparator = comparator.reversed();
        }

        // 文件夹始终排在前面
        Comparator<FileItem> directoryFirst = Comparator
                .comparing(FileItem::isDirectory)
                .reversed()
                .thenComparing(comparator);

        files.sort(directoryFirst);
    }

    private static Comparator<FileItem> getComparator(SortBy sortBy) {
        return switch (sortBy) {
            case NAME -> Comparator.comparing(FileItem::getName, String.CASE_INSENSITIVE_ORDER);
            case SIZE -> Comparator.comparingLong(FileItem::getSize);
            case TYPE -> Comparator.comparing(FileItem::getType);  // 使用 getFileType()
            //case DATE_CREATED -> Comparator.comparing(FileItem::getCreateTime);
            case DATE_CREATED -> null;
            case DATE_MODIFIED -> Comparator.comparing(FileItem::getModifyTime);
        };
    }
}