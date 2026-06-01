package com.fileexplorer.controller;

import com.fileexplorer.model.FileItem;
import com.fileexplorer.service.FileOperationService;
import com.fileexplorer.util.SearchUtils;
import com.fileexplorer.util.SortUtils;
import com.fileexplorer.util.DiskUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// 在 MainController.java 的顶部添加
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.layout.HBox;

public class MainController {

    // 添加实时搜索相关变量
    private boolean enableRealtimeSearch = false; // 控制是否启用实时搜索
    private Timer searchTimer; // 用于实时搜索的防抖定时器
    private final long REALTIME_SEARCH_DELAY = 500; // 实时搜索延迟（毫秒）

    // 可选：添加搜索历史相关变量
    private List<String> searchHistory = new ArrayList<>();


    @FXML private BorderPane mainPane;
    @FXML private TreeView<String> directoryTree;
    @FXML private TableView<FileItem> fileTable;
    @FXML private TextField pathField;
    @FXML private TextField searchField;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;

    // 搜索相关字段

    @FXML
    private ComboBox<String> searchTypeCombo;
    @FXML
    private CheckBox caseSensitiveCheckBox;
    @FXML
    private Button clearSearchButton;
    @FXML
    private HBox advancedSearchOptions;  // 用于控制高级搜索选项的显示/隐藏
    @FXML
    private Button historyButton;
    @FXML
    private HBox searchHistoryBox;


    // 文件列表列 - 添加这些字段
    @FXML private TableColumn<FileItem, String> nameColumn;
    @FXML private TableColumn<FileItem, String> sizeColumn;
    @FXML private TableColumn<FileItem, String> typeColumn;
    @FXML private TableColumn<FileItem, String> modifiedColumn;

    // 文件列表相关
    private ObservableList<FileItem> allFileItems = FXCollections.observableArrayList();
    private FilteredList<FileItem> filteredFileItems = new FilteredList<>(allFileItems, p -> true);
    private SortedList<FileItem> sortedFileItems = new SortedList<>(filteredFileItems);

    // 搜索相关状态
    private String lastSearchText = "";

    // 剪贴板相关
    private enum ClipboardOperation { COPY, CUT }
    private ClipboardOperation clipboardOperation = null;
    private List<FileItem> clipboardItems = new ArrayList<>();
    private boolean clipboardHasItems = false;

    private ContextMenu directoryTreeContextMenu;
    private ContextMenu fileTableContextMenu;
    private MenuItem newFolderMenuItem;
    private MenuItem newFileMenuItem;
    private MenuItem renameMenuItem;
    private MenuItem deleteMenuItem;
    private MenuItem refreshMenuItem;
    private MenuItem copyMenuItem;
    private MenuItem cutMenuItem;
    private MenuItem pasteMenuItem;

    // 记录当前路径
    private Path currentPath = Paths.get(".").toAbsolutePath().normalize();
    // 当前位置节点
    private TreeItem<String> currentLocationNode = null;
    // 控制变量，避免在更新目录树时触发事件
    private boolean updatingTree = false;
    // 记录上一次点击的时间，防止快速重复点击
    private long lastClickTime = 0;

    // 在 MainController 类中添加这些字段
    private List<Path> navigationHistory = new ArrayList<>();
    private int currentHistoryIndex = -1;
    private boolean isNavigating = false;
    @FXML
    private Label diskSpaceLabel;  // 磁盘空间标签引用
    // 磁盘空间监控相关变量
    private Timer diskSpaceTimer;  // 磁盘空间定时器
    private ScheduledExecutorService diskSpaceScheduler;  // 磁盘空间调度服务
    private boolean isDiskSpaceMonitoring = false;  // 磁盘空间监控状态
    private long diskSpaceUpdateInterval = 5000;  // 磁盘空间更新间隔(5秒)
/**
 * 设置双击事件 - 已合并到 setupTableRowFactory 中
 */
// 移除这个方法，双击事件已在 setupTableRowFactory 中处理
// private void setupDoubleClickEvent() { ... }

// 修改 initialize 方法
@FXML
public void initialize() {
    System.out.println("控制器初始化开始");

    // 1. 先初始化所有UI组件
    initializeUIComponents();

    // 2. 设置表格列
    setupTableColumns();

    // 3. 初始化搜索组件
    initializeSearchComponents();

    // 4. 初始化目录树
    System.out.println("开始初始化目录树...");
    try {
        initializeDirectoryTree();
    } catch (Exception e) {
        System.err.println("初始化目录树失败: " + e.getMessage());
    }

    // 5. 设置表格
    fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    fileTable.setRowFactory(tv -> {
        TableRow<FileItem> row = new TableRow<>();
        row.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !row.isEmpty()) {
                handleDoubleClick(row.getItem());
                event.consume();
            }
        });
        return row;
    });

    // 6. 初始化右键菜单
    initContextMenus();

    // 7. 延迟加载初始目录
    Platform.runLater(() -> {
        if (currentPath != null) {
            System.out.println("延迟刷新目录: " + currentPath);
            refreshDirectory();
        } else {
            // 设置默认路径
            currentPath = Paths.get(System.getProperty("user.home"));
            System.out.println("设置默认路径: " + currentPath);
            refreshDirectory();
        }

        // 8. 设置键盘快捷键
        try {
            setupKeyboardShortcuts();
        } catch (Exception e) {
            System.err.println("设置键盘快捷键失败: " + e.getMessage());
        }

        // 9. 启动磁盘空间监控
        initializeDiskSpaceMonitoring();
        // 在 initialize() 方法中添加表格选择监听器
fileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
    if (newSelection != null) {
        // 如果是文件夹，显示详细信息
        if (newSelection.isDirectory()) {
            Platform.runLater(() -> {
                showSelectedFolderDetails(newSelection);
            });
        } else {
            // 如果是文件，显示文件信息
            Platform.runLater(() -> {
                showSelectedFileDetails(newSelection);
            });
        }
    }
});


        // 10. 确保表格有焦点
        fileTable.setFocusTraversable(true);
    });

    System.out.println("控制器初始化完成");
}
    /**
     * 更新搜索历史显示
     */

    // 简化 addToSearchHistory 方法
    private void addToSearchHistory(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return;
        }

        searchHistory.remove(searchText);
        searchHistory.add(0, searchText);

        if (searchHistory.size() > 20) {
            searchHistory.remove(searchHistory.size() - 1);
        }

        currentHistoryIndex = -1;
        System.out.println("已添加到搜索历史: " + searchText);
    }
    /**
     * 初始化磁盘空间监控
     */
    private void initializeDiskSpaceMonitoring() {
        try {
            System.out.println("初始化磁盘空间监控...");

            // 立即更新一次磁盘空间
            updateDiskSpaceInfo();

            // 创建定时任务，定期更新磁盘空间
            diskSpaceScheduler = Executors.newSingleThreadScheduledExecutor();
            diskSpaceScheduler.scheduleAtFixedRate(
                    this::updateDiskSpaceInfo,
                    0,  // 初始延迟
                    diskSpaceUpdateInterval,  // 更新间隔
                    TimeUnit.MILLISECONDS
            );

            isDiskSpaceMonitoring = true;
            System.out.println("磁盘空间监控已启动");

        } catch (Exception e) {
            System.err.println("初始化磁盘空间监控失败: " + e.getMessage());
        }
    }
    /**
     * 更新磁盘空间信息
     */
    private void updateDiskSpaceInfo() {
        if (currentPath == null) {
            return;
        }

        try {
            // 获取当前路径所在的磁盘
            DiskUtils.DiskInfo currentDisk = getCurrentDiskInfo();

            if (currentDisk != null) {
                // 计算磁盘使用率
                double usagePercent = currentDisk.getUsagePercentage();
                long freeSpace = currentDisk.getFreeSpace();
                long totalSpace = currentDisk.getTotalSpace();
                long usedSpace = currentDisk.getUsedSpace();

                // 格式化显示文本
                String diskText = formatDiskSpaceText(currentDisk, usagePercent, freeSpace, totalSpace, usedSpace);

                // 更新UI
                Platform.runLater(() -> {
                    if (diskSpaceLabel != null) {
                        diskSpaceLabel.setText(diskText);

                        // 根据使用率设置颜色
                        if (usagePercent > 90) {
                            diskSpaceLabel.setStyle("-fx-text-fill: #f44336; -fx-font-size: 12px;");
                        } else if (usagePercent > 80) {
                            diskSpaceLabel.setStyle("-fx-text-fill: #FF9800; -fx-font-size: 12px;");
                        } else if (usagePercent > 60) {
                            diskSpaceLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 12px;");
                        } else {
                            diskSpaceLabel.setStyle("-fx-text-fill: #2196F3; -fx-font-size: 12px;");
                        }

                        // 设置工具提示显示更详细信息
                        setDiskSpaceTooltip(currentDisk, usagePercent, freeSpace, totalSpace, usedSpace);
                    }
                });
            }

        } catch (Exception e) {
            Platform.runLater(() -> {
                if (diskSpaceLabel != null) {
                    diskSpaceLabel.setText("磁盘空间: 未知");
                }
            });
        }
    }
    /**
     * 获取当前路径所在磁盘的信息
     */
    private DiskUtils.DiskInfo getCurrentDiskInfo() {
        try {
            if (currentPath == null) {
                return null;
            }

            // 如果是磁盘根目录，直接获取
            if (isDiskRoot(currentPath.toString())) {
                return DiskUtils.getDiskInfo(currentPath);
            }

            // 否则获取当前路径所在的磁盘
            Path diskRoot = getDiskRoot(currentPath);
            if (diskRoot != null) {
                return DiskUtils.getDiskInfo(diskRoot);
            }

        } catch (Exception e) {
            System.err.println("获取磁盘信息失败: " + e.getMessage());
        }
        return null;
    }
    /**
     * 获取路径所在的磁盘根目录
     */
    private Path getDiskRoot(Path path) {
        if (path == null) {
            return null;
        }

        try {
            // 获取文件系统
            FileSystem fileSystem = FileSystems.getDefault();

            // 遍历所有根目录
            for (Path root : fileSystem.getRootDirectories()) {
                if (path.startsWith(root)) {
                    return root;
                }
            }
        } catch (Exception e) {
            System.err.println("获取磁盘根目录失败: " + e.getMessage());
        }

        return null;
    }
    /**
     * 格式化磁盘空间显示文本
     */
    private String formatDiskSpaceText(DiskUtils.DiskInfo disk, double usagePercent,
                                       long freeSpace, long totalSpace, long usedSpace) {
        String diskName = disk.getName();
        String diskPath = disk.getPath();

        // 提取磁盘字母
        String diskLetter = "";
        if (diskPath.length() >= 2) {
            diskLetter = diskPath.substring(0, 2);
        }

        // 简化的显示格式
        if (totalSpace > 0) {
            String freeSpaceStr = formatFileSize(freeSpace);
            String usedPercentStr = String.format("%.1f", usagePercent);

            return String.format("磁盘 %s: %s 可用 (已用 %s%%)",
                    diskLetter, freeSpaceStr, usedPercentStr);
        }

        return "磁盘空间: 未知";
    }
    /**
     * 设置磁盘空间工具提示
     */
    private void setDiskSpaceTooltip(DiskUtils.DiskInfo disk, double usagePercent,
                                     long freeSpace, long totalSpace, long usedSpace) {
        if (diskSpaceLabel != null && disk != null) {
            StringBuilder tooltipText = new StringBuilder();
            tooltipText.append("磁盘详细信息\n");
            tooltipText.append("============\n");
            tooltipText.append("磁盘名称: ").append(disk.getName()).append("\n");
            tooltipText.append("磁盘路径: ").append(disk.getPath()).append("\n");
            tooltipText.append("总空间: ").append(formatFileSize(totalSpace)).append("\n");
            tooltipText.append("已用空间: ").append(formatFileSize(usedSpace)).append("\n");
            tooltipText.append("可用空间: ").append(formatFileSize(freeSpace)).append("\n");
            tooltipText.append("使用率: ").append(String.format("%.1f%%", usagePercent)).append("\n");

            // 显示进度条
            tooltipText.append("\n使用情况: [");
            int barLength = 20;
            int usedBars = (int) (usagePercent / 100 * barLength);

            for (int i = 0; i < barLength; i++) {
                if (i < usedBars) {
                    tooltipText.append("█");
                } else {
                    tooltipText.append("░");
                }
            }
            tooltipText.append("]\n");

            Tooltip tooltip = new Tooltip(tooltipText.toString());
            tooltip.setShowDelay(javafx.util.Duration.millis(300));
            diskSpaceLabel.setTooltip(tooltip);
        }
    }/**
     * 检查是否是磁盘根目录
     */
    private boolean isDiskRoot(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        // Windows磁盘根目录格式: C:\
        if (path.length() == 3 && path.charAt(1) == ':' && path.charAt(2) == '\\') {
            return true;
        }

        // Unix根目录格式: /
        if (path.equals("/")) {
            return true;
        }

        return false;
    }
    /**
     * 停止磁盘空间监控
     */
    private void stopDiskSpaceMonitoring() {
        if (diskSpaceScheduler != null && !diskSpaceScheduler.isShutdown()) {
            diskSpaceScheduler.shutdown();
            try {
                if (!diskSpaceScheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    diskSpaceScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                diskSpaceScheduler.shutdownNow();
            }
        }

        isDiskSpaceMonitoring = false;
        System.out.println("磁盘空间监控已停止");
    }
    /**
     * 切换磁盘空间监控开关
     */
    public void toggleDiskSpaceMonitoring(boolean enable) {
        if (enable && !isDiskSpaceMonitoring) {
            initializeDiskSpaceMonitoring();
        } else if (!enable && isDiskSpaceMonitoring) {
            stopDiskSpaceMonitoring();
        }
    }
    /**
     * 获取所有磁盘的详细信息
     */
    public List<DiskUtils.DiskInfo> getAllDiskInfo() {
        return DiskUtils.getDiskDrives();
    }
    /**
     * 显示所有磁盘空间概览
     */
    public void showAllDiskSpaceOverview() {
        List<DiskUtils.DiskInfo> allDisks = getAllDiskInfo();
        if (allDisks.isEmpty()) {
            return;
        }

        StringBuilder overview = new StringBuilder();
        overview.append("所有磁盘空间概览\n");
        overview.append("================\n");

        for (DiskUtils.DiskInfo disk : allDisks) {
            double usagePercent = disk.getUsagePercentage();
            overview.append(String.format("%s (%s): %s 可用 / %s 总共 (已用 %.1f%%)\n",
                    disk.getName(),
                    disk.getPath(),
                    formatFileSize(disk.getFreeSpace()),
                    formatFileSize(disk.getTotalSpace()),
                    usagePercent));
        }

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("磁盘空间概览");
            alert.setHeaderText("所有磁盘使用情况");
            alert.setContentText(overview.toString());
            alert.show();
        });
    }
    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else if (size < 1024L * 1024 * 1024 * 1024) {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        } else {
            return String.format("%.1f TB", size / (1024.0 * 1024.0 * 1024.0 * 1024.0));
        }
    }
    /**
     * 显示搜索历史菜单
     */
    @FXML
    private void showSearchHistoryMenu() {
        if (searchField == null || searchHistory == null || searchHistory.isEmpty()) {
            // 如果没有历史记录，显示提示
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("搜索历史");
                alert.setHeaderText(null);
                alert.setContentText("暂无搜索历史记录");
                alert.show();
            });
            return;
        }

        // 创建上下文菜单
        ContextMenu historyMenu = new ContextMenu();

        // 添加历史记录（最多10条）
        for (int i = 0; i < Math.min(searchHistory.size(), 10); i++) {
            String historyItem = searchHistory.get(i);
            MenuItem menuItem = new MenuItem((i+1) + ". " + historyItem);
            menuItem.setOnAction(e -> {
                searchField.setText(historyItem);
                searchField.positionCaret(historyItem.length());
                searchFiles();
            });
            historyMenu.getItems().add(menuItem);
        }

        // 添加分隔线
        historyMenu.getItems().add(new SeparatorMenuItem());

        // 添加清除历史选项
        MenuItem clearItem = new MenuItem("清除历史记录");
        clearItem.setOnAction(e -> clearSearchHistory());
        historyMenu.getItems().add(clearItem);

        // 显示菜单
        historyMenu.show(historyButton, Side.BOTTOM, 0, 0);
    }



    /**
     * 增强的实时搜索功能
     */
    /**
     * 增强的实时搜索功能
     */
    private void setupEnhancedRealtimeSearch() {
        if (searchField != null) {
            // 控制实时搜索的开关
            enableRealtimeSearch = true; // 设置为 true 启用实时搜索

            // 初始化搜索历史
            searchHistory = new ArrayList<>();

            // 文本变化监听器
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (enableRealtimeSearch) {
                    scheduleRealtimeSearch(newValue);
                }
            });

            // 添加键盘快捷键
            setupSearchKeyboardShortcuts();

            // 添加搜索历史功能
            setupSearchHistory();

            // 设置工具提示
            Tooltip searchTooltip = new Tooltip();
            searchTooltip.setText(
                    "搜索功能帮助:\n" +
                            "• 输入文件名搜索 (支持 * 和 ? 通配符)\n" +
                            "• 按 Enter 键搜索\n" +
                            "• 按 Ctrl+F 聚焦搜索框\n" +
                            "• 按 ESC 清除搜索\n" +
                            "• 按 上下箭头 导航搜索历史\n" +
                            "• 按 Ctrl+H 显示搜索历史菜单"
            );
            searchField.setTooltip(searchTooltip);
        }
    }
    /**
     * 设置搜索历史功能
     */
    private void setupSearchHistory() {
        if (searchField == null) {
            return;
        }

        // 初始化搜索历史列表
        searchHistory = new ArrayList<>();

        // 创建一个搜索历史下拉列表
        ContextMenu searchHistoryMenu = new ContextMenu();
        searchField.setContextMenu(searchHistoryMenu);

        // 监听搜索框焦点事件
        searchField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                // 获得焦点时显示搜索历史
                showSearchHistoryMenu();
            }
        });

        // 监听键盘上下箭头键
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP) {
                // 上箭头：导航到上一个历史记录
                navigateSearchHistory(-1);
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                // 下箭头：导航到下一个历史记录
                navigateSearchHistory(1);
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.H) {
                // Ctrl+H：显示搜索历史
                showSearchHistoryMenu();
                event.consume();
            }
        });

        // 监听回车键，将搜索记录添加到历史
        searchField.setOnAction(event -> {
            String searchText = searchField.getText().trim();
            if (!searchText.isEmpty() && !searchHistory.contains(searchText)) {
                addToSearchHistory(searchText);
            }
        });
    }



    /**
     * 显示搜索历史菜单
     */
//    @FXML
//    private void showSearchHistoryMenu() {
//        if (searchField == null || searchHistory == null || searchHistory.isEmpty()) {
//            return;
//        }
//
//        ContextMenu historyMenu = searchField.getContextMenu();
//        if (historyMenu == null) {
//            historyMenu = new ContextMenu();
//            searchField.setContextMenu(historyMenu);
//        }
//
//        // 清空现有菜单项
//        historyMenu.getItems().clear();
//
//        // 添加历史记录
//        for (int i = 0; i < Math.min(searchHistory.size(), 10); i++) {
//            String historyItem = searchHistory.get(i);
//            MenuItem menuItem = new MenuItem(historyItem);
//            menuItem.setOnAction(e -> {
//                searchField.setText(historyItem);
//                searchField.positionCaret(historyItem.length());
//                searchFiles();
//            });
//            historyMenu.getItems().add(menuItem);
//        }
//
//        // 添加清除历史选项
//        if (!searchHistory.isEmpty()) {
//            historyMenu.getItems().add(new SeparatorMenuItem());
//            MenuItem clearItem = new MenuItem("清除历史记录");
//            clearItem.setOnAction(e -> clearSearchHistory());
//            historyMenu.getItems().add(clearItem);
//        }
//
//        // 显示菜单
//        if (!historyMenu.getItems().isEmpty()) {
//            historyMenu.show(searchField, Side.BOTTOM, 0, 0);
//        }
//    }

    /**
     * 清除搜索历史
     */
    private void clearSearchHistory() {
        if (searchHistory != null) {
            searchHistory.clear();
            currentHistoryIndex = -1;

            // 移除上下文菜单
            if (searchField != null) {
                searchField.setContextMenu(null);
            }

            System.out.println("已清除搜索历史");

            // 显示确认消息
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("搜索历史");
                alert.setHeaderText(null);
                alert.setContentText("搜索历史已清除");
                alert.show();
            });
        }
    }

    /**
     * 导航搜索历史
     */
    private void navigateSearchHistory(int direction) {
        if (searchHistory == null || searchHistory.isEmpty()) {
            return;
        }

        if (direction < 0) { // 上箭头：上一个历史记录
            if (currentHistoryIndex < 0) {
                currentHistoryIndex = searchHistory.size() - 1;
            } else if (currentHistoryIndex > 0) {
                currentHistoryIndex--;
            }
        } else { // 下箭头：下一个历史记录
            if (currentHistoryIndex < searchHistory.size() - 1) {
                currentHistoryIndex++;
            } else {
                currentHistoryIndex = -1;
            }
        }

        if (currentHistoryIndex >= 0 && currentHistoryIndex < searchHistory.size()) {
            String historyText = searchHistory.get(currentHistoryIndex);
            searchField.setText(historyText);
            searchField.positionCaret(historyText.length());
        } else if (currentHistoryIndex == -1) {
            // 回到当前输入
            searchField.setText("");
        }
    }

    /**
     * 调度实时搜索（防抖处理）
     */
    private void scheduleRealtimeSearch(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            // 如果清空了搜索框，刷新显示所有文件
            if (searchTimer != null) {
                searchTimer.cancel();
            }
            refreshDirectory();
            return;
        }

        // 取消之前的定时器
        if (searchTimer != null) {
            searchTimer.cancel();
        }

        // 创建新的定时器
        searchTimer = new Timer("SearchTimer", true);

        // 创建搜索任务
        TimerTask searchTask = new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    // 获取当前搜索文本
                    String currentText = searchField.getText();

                    // 如果搜索框不为空，执行搜索
                    if (currentText != null && !currentText.trim().isEmpty()) {
                        System.out.println("执行实时搜索: " + currentText);
                        searchFiles();

                        // 记录搜索历史（去重）
                        if (!searchHistory.contains(currentText)) {
                            searchHistory.add(currentText);
                            if (searchHistory.size() > 20) { // 限制历史记录数量
                                searchHistory.remove(0);
                            }
                        }
                    }
                });
            }
        };

        // 延迟执行搜索
        searchTimer.schedule(searchTask, REALTIME_SEARCH_DELAY);
    }

    /**
     * 设置搜索键盘快捷键
     */
    private void setupSearchKeyboardShortcuts() {
        // Ctrl+F 聚焦搜索框
        Platform.runLater(() -> {
            Scene scene = searchField.getScene();
            if (scene != null) {
                scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.isControlDown() && event.getCode() == KeyCode.F) {
                        searchField.requestFocus();
                        searchField.selectAll();
                        event.consume();
                    } else if (event.getCode() == KeyCode.ESCAPE) {
                        // ESC 清除搜索
                        clearSearch();
                        event.consume();
                    } else if (event.getCode() == KeyCode.ENTER && searchField.isFocused()) {
                        // 在搜索框按 Enter 执行搜索
                        searchFiles();
                        event.consume();
                    }
                });
            }
        });
    }

    /**
     * 设置搜索历史导航
     */
    private void setupSearchHistoryNavigation() {
        Platform.runLater(() -> {
            Scene scene = searchField.getScene();
            if (scene != null) {
                scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (searchField.isFocused() && event.getCode() == KeyCode.UP) {
                        // 上箭头：搜索历史上一项
                        navigateSearchHistory(-1);
                        event.consume();
                    } else if (searchField.isFocused() && event.getCode() == KeyCode.DOWN) {
                        // 下箭头：搜索历史下一项
                        navigateSearchHistory(1);
                        event.consume();
                    }
                });
            }
        });
    }


    /**
     * 清除搜索
     */
    @FXML
    private void clearSearch() {
        if (searchField != null) {
            searchField.clear();
        }

        // 重置历史索引
        currentHistoryIndex = -1;

        // 刷新目录显示
        refreshDirectory();

        // 取消实时搜索定时器
        if (searchTimer != null) {
            searchTimer.cancel();
            searchTimer = null;
        }
    }


    /**
     * 切换实时搜索开关
     */
    public void toggleRealtimeSearch(boolean enabled) {
        this.enableRealtimeSearch = enabled;
        System.out.println("实时搜索: " + (enabled ? "启用" : "禁用"));
    }
//
    private void addDebugListeners() {
        // 监听选择变化
        fileTable.getSelectionModel().getSelectedIndices().addListener(
                (ListChangeListener<Integer>) change -> {
                    System.out.println("选择变化: " + change.getList());
                }
        );

        // 监听表格上的键盘事件
        fileTable.setOnKeyPressed(event -> {
            System.out.println("表格键盘按下: Ctrl=" + event.isControlDown() +
                    ", Shift=" + event.isShiftDown() +
                    ", Key=" + event.getCode());
        });

        // 监听表格上的鼠标事件
        fileTable.setOnMouseClicked(event -> {
            System.out.println("表格鼠标点击: Ctrl=" + event.isControlDown() +
                    ", Shift=" + event.isShiftDown());
        });
    }
    /**
     * 搜索框焦点和提示处理
     */
    private void setupSearchField() {
        if (searchField != null) {
            // 添加焦点监听
            searchField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    // 获得焦点时选中所有文本
                    Platform.runLater(() -> {
                        searchField.selectAll();
                    });
                }
            });

            // 添加文本变化监听，实现实时搜索（可选）
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                // 如果开启实时搜索，可以在这里调用搜索
                // 但可能影响性能，建议保留搜索按钮
            });

            // 添加快捷键
            searchField.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.F) {
                    searchField.requestFocus();
                    event.consume();
                }
            });
        }
    }



    /**
     * 设置表格列
     */
    private void setupTableColumns() {
        if (fileTable == null) {
            System.err.println("错误: fileTable 为 null");
            return;
        }

        System.out.println("开始设置表格列...");

        // 清除现有列
        fileTable.getColumns().clear();

        // 名称列
        TableColumn<FileItem, String> nameColumn = new TableColumn<>("名称");
        nameColumn.setPrefWidth(250);
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());

        // 大小列
        TableColumn<FileItem, String> sizeColumn = new TableColumn<>("大小");
        sizeColumn.setPrefWidth(100);
        sizeColumn.setCellValueFactory(cellData -> {
            FileItem item = cellData.getValue();
            if (item.isDirectory()) {
                return new SimpleStringProperty("");
            } else {
                return new SimpleStringProperty(formatFileSize(item.getSize()));
            }
        });

        // 类型列
        TableColumn<FileItem, String> typeColumn = new TableColumn<>("类型");
        typeColumn.setPrefWidth(100);
        typeColumn.setCellValueFactory(cellData -> {
            FileItem item = cellData.getValue();
            return new SimpleStringProperty(item.getType().toString());
        });

        // 修改时间列
        TableColumn<FileItem, String> modifiedColumn = new TableColumn<>("修改时间");
        modifiedColumn.setPrefWidth(150);
        modifiedColumn.setCellValueFactory(cellData -> {
            FileItem item = cellData.getValue();
            if (item.getModifyTime() != null) {
                return new SimpleStringProperty(item.getModifyTime().toString());
            } else {
                return new SimpleStringProperty("");
            }
        });

        // 将列添加到表格
        fileTable.getColumns().addAll(nameColumn, sizeColumn, typeColumn, modifiedColumn);

        System.out.println("表格列设置完成");
    }

    /**
     * 初始化UI组件
     */
    private void initializeUIComponents() {
        // 1. 初始化状态栏
        if (statusLabel != null) {
            statusLabel.setText("就绪");
        } else {
            System.err.println("错误: statusLabel 注入失败!");
        }

        // 2. 初始化进度条
        if (progressBar != null) {
            progressBar.setProgress(0);
            progressBar.setVisible(false);
        } else {
            System.err.println("警告: progressBar 注入失败!");
        }

        // 3. 检查关键组件
        if (pathField == null) {
            System.err.println("错误: pathField 注入失败!");
        } else {
            System.out.println("pathField 注入成功");
        }

        if (fileTable == null) {
            System.err.println("错误: fileTable 注入失败!");
        } else {
            System.out.println("fileTable 注入成功");
        }

        if (directoryTree == null) {
            System.err.println("错误: directoryTree 注入失败!");
        } else {
            System.out.println("directoryTree 注入成功");
        }
    }

    /**
     * 初始化搜索组件
     */
    // 修改 initializeSearchComponents 方法
    private void initializeSearchComponents() {
        // 初始化搜索类型下拉框
        if (searchTypeCombo != null) {
            ObservableList<String> searchOptions = FXCollections.observableArrayList(
                    "文件名", "文件扩展名", "内容包含", "大文件", "图片", "文档", "视频", "音频"
            );
            searchTypeCombo.setItems(searchOptions);
            searchTypeCombo.getSelectionModel().selectFirst(); // 默认选择第一个
        }

        // 设置大小写敏感复选框
        if (caseSensitiveCheckBox != null) {
            caseSensitiveCheckBox.setSelected(false); // 默认不区分大小写
        }

        // 设置清除搜索按钮
        if (clearSearchButton != null) {
            clearSearchButton.setOnAction(e -> {
                if (searchField != null) {
                    searchField.clear();
                    refreshDirectory(); // 清空搜索后刷新显示所有文件
                }
            });
        }

        // 设置搜索框的键盘事件（回车搜索）
        if (searchField != null) {
            searchField.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    searchFiles();
                }
            });
        }

        // 可选：添加一个按钮切换高级搜索选项
        addAdvancedSearchToggle();
    }
    /**
     * 添加高级搜索切换按钮
     */
    private void addAdvancedSearchToggle() {
        // 可以在工具栏添加一个按钮来切换高级搜索选项
        // 或者在搜索区域添加一个小的展开/收起图标
        // 这里提供一种实现方式

        // 创建切换按钮
        Button toggleAdvanced = new Button("高级搜索");
        toggleAdvanced.setStyle("-fx-background-color: transparent; -fx-text-fill: #2196F3;");
        toggleAdvanced.setOnAction(e -> {
            boolean visible = advancedSearchOptions.isVisible();
            advancedSearchOptions.setVisible(!visible);
            toggleAdvanced.setText(visible ? "高级搜索" : "收起");
        });

        // 将按钮添加到工具栏（需要修改FXML或动态添加）
        // 这里提供代码思路，您可以根据需要调整
    }


    @FXML
    private void goBack() {
        try {
            if (navigationHistory.isEmpty() || currentHistoryIndex <= 0) {
                showInfo("已到历史记录开头");
                return;
            }

            currentHistoryIndex--;
            isNavigating = true;

            // 获取历史路径
            Path historyPath = navigationHistory.get(currentHistoryIndex);

            // 检查路径是否存在
            if (!Files.exists(historyPath) || !Files.isDirectory(historyPath)) {
                showError("历史路径不存在: " + historyPath);
                navigationHistory.remove(currentHistoryIndex);
                currentHistoryIndex++;
                return;
            }

            // 设置当前路径
            currentPath = historyPath;

            // 刷新目录
            refreshDirectory();

            isNavigating = false;

        } catch (Exception e) {
            isNavigating = false;
            System.err.println("后退失败: " + e.getMessage());
            e.printStackTrace();
            showError("后退失败: " + e.getMessage());
        }
    }

    @FXML
    private void goForward() {
        try {
            if (navigationHistory.isEmpty() || currentHistoryIndex >= navigationHistory.size() - 1) {
                showInfo("已到历史记录末尾");
                return;
            }

            currentHistoryIndex++;
            isNavigating = true;

            // 获取历史路径
            Path historyPath = navigationHistory.get(currentHistoryIndex);

            // 检查路径是否存在
            if (!Files.exists(historyPath) || !Files.isDirectory(historyPath)) {
                showError("历史路径不存在: " + historyPath);
                navigationHistory.remove(currentHistoryIndex);
                currentHistoryIndex--;
                return;
            }

            // 设置当前路径
            currentPath = historyPath;

            // 刷新目录
            refreshDirectory();

            isNavigating = false;

        } catch (Exception e) {
            isNavigating = false;
            System.err.println("前进失败: " + e.getMessage());
            e.printStackTrace();
            showError("前进失败: " + e.getMessage());
        }
    }

    /**
     * 添加到历史记录
     */
    private void addToHistory(Path path) {
        if (isNavigating) {
            return;  // 如果是通过前进后退导航，不添加历史记录
        }

        // 如果当前有历史记录，删除当前位置之后的所有记录
        if (currentHistoryIndex < navigationHistory.size() - 1) {
            navigationHistory = navigationHistory.subList(0, currentHistoryIndex + 1);
        }

        // 添加新路径
        navigationHistory.add(path);
        currentHistoryIndex = navigationHistory.size() - 1;

        // 限制历史记录数量
        if (navigationHistory.size() > 50) {
            navigationHistory = navigationHistory.subList(navigationHistory.size() - 50, navigationHistory.size());
            currentHistoryIndex = navigationHistory.size() - 1;
        }
    }

    /**
     * 向上到父目录
     */
    @FXML
    private void goUp() {
        try {
            if (currentPath == null || currentPath.getParent() == null) {
                return;
            }

            Path parentPath = currentPath.getParent();
            if (Files.exists(parentPath) && Files.isDirectory(parentPath)) {
                addToHistory(parentPath);
                navigateToFolder(parentPath.toString());
            }
        } catch (Exception e) {
            showError("无法向上: " + e.getMessage());
        }
    }



    /**
     * 导航到指定路径
     */
    /**
     * 导航到指定的路径
     */
    @FXML
    public void navigateToPath() {  // 移除参数
        try {
            if (pathField == null) {
                showError("路径输入框未初始化");
                return;
            }

            String pathText = pathField.getText();
            if (pathText == null || pathText.trim().isEmpty()) {
                return;
            }

            System.out.println("尝试导航到: " + pathText);

            Path newPath = Paths.get(pathText).toAbsolutePath().normalize();

            if (!Files.exists(newPath)) {
                showError("路径不存在: " + newPath);
                return;
            }

            if (!Files.isDirectory(newPath)) {
                showError("这不是一个文件夹: " + newPath);
                return;
            }

            // 调用 navigateToFolder
            navigateToFolder(newPath.toString());

            // 在成功导航后添加历史记录
            if (!isNavigating) {
                addToHistory(currentPath);
            }

        } catch (Exception e) {
            System.err.println("导航失败: " + e.getMessage());
            e.printStackTrace();
            showError("路径无效: " + e.getMessage());
        }
    }

    /**
     * 设置文件表格
     */
    private void setupFileTable() {
        // 初始化列表
        allFileItems = FXCollections.observableArrayList();
        filteredFileItems = new FilteredList<>(allFileItems, p -> true);
        sortedFileItems = new SortedList<>(filteredFileItems);

        // 绑定到表格
        sortedFileItems.comparatorProperty().bind(fileTable.comparatorProperty());
        fileTable.setItems(sortedFileItems);
    }

    /**
     * 设置搜索功能
     */
    private void setupSearchFunctionality() {
        if (searchField != null) {
            // 实时搜索监听
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                applySearchFilter();
            });

            // 搜索类型变化监听
            if (searchTypeCombo != null) {
                searchTypeCombo.getSelectionModel().selectedItemProperty().addListener(
                        (observable, oldValue, newValue) -> {
                            applySearchFilter();
                        }
                );

                // 设置默认值
                searchTypeCombo.getSelectionModel().selectFirst();
            }

            // 大小写敏感监听
            if (caseSensitiveCheckBox != null) {
                caseSensitiveCheckBox.selectedProperty().addListener(
                        (observable, oldValue, newValue) -> {
                            applySearchFilter();
                        }
                );
            }

            // 清除搜索按钮
            if (clearSearchButton != null) {
                clearSearchButton.setOnAction(e -> {
                    clearSearch();
                });
            }
        }
    }

    /**
     * 初始化文件表格
     */
    private void initializeFileTable() {
        // 设置列和数据
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("formattedSize"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        modifiedColumn.setCellValueFactory(new PropertyValueFactory<>("modifiedTime"));

        // 设置可排序
        nameColumn.setSortType(TableColumn.SortType.ASCENDING);

        // 绑定排序列表到表格
        sortedFileItems.comparatorProperty().bind(fileTable.comparatorProperty());
        fileTable.setItems(sortedFileItems);

        // 双击打开
        fileTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !event.isConsumed()) {
                openSelectedItem();
            }
        });
    }
    /**
     * 应用搜索过滤器
     */
    private void applySearchFilter() {
        if (searchField == null) {
            return;
        }

        String searchText = searchField.getText().trim();
        String searchType = searchTypeCombo != null ? searchTypeCombo.getValue() : "文件名";
        boolean caseSensitive = caseSensitiveCheckBox != null && caseSensitiveCheckBox.isSelected();

        // 如果没有搜索内容，显示所有文件
        if (searchText.isEmpty()) {
            filteredFileItems.setPredicate(item -> true);
            updateSearchStatus();
            return;
        }

        // 应用过滤器
        filteredFileItems.setPredicate(item -> {
            String textToSearch = searchText;
            String itemName = item.getName();

            if (!caseSensitive) {
                textToSearch = textToSearch.toLowerCase();
                itemName = itemName.toLowerCase();
            }

            // 根据搜索类型进行过滤
            if ("文件名".equals(searchType)) {
                return itemName.contains(textToSearch);
            } else if ("文件扩展名".equals(searchType)) {
                // 检查文件扩展名
                String extension = getFileExtension(itemName);
                if (extension.isEmpty()) {
                    return false; // 无扩展名的文件
                }
                return caseSensitive ?
                        extension.contains(searchText) :
                        extension.toLowerCase().contains(textToSearch);
            } else if ("内容包含".equals(searchType)) {
                // 对于文本文件，可以检查内容（简化版本）
                // 这里我们只检查文件名，实际应用中应该读取文件内容
                return itemName.contains(textToSearch);
            }

            return true; // 默认不过滤
        });

        updateSearchStatus();
    }

    /**
     * 更新搜索状态
     */
    private void updateSearchStatus() {
        String searchText = searchField.getText().trim();
        int totalCount = allFileItems.size();
        int filteredCount = filteredFileItems.size();

        if (searchText.isEmpty()) {
            // 如果没有搜索文本，显示常规状态
            int folderCount = 0;
            int fileCount = 0;
            long totalSize = 0;

            for (FileItem file : allFileItems) {
                if (file.isDirectory()) {
                    folderCount++;
                } else {
                    fileCount++;
                    totalSize += file.getSize();
                }
            }

            String sizeStr = formatFileSize(totalSize);
            String statusText = "路径: " + currentPath.toString() +
                    " | 总计: " + totalCount +
                    " (文件: " + fileCount +
                    ", 文件夹: " + folderCount +
                    ", 大小: " + sizeStr + ")";
            statusLabel.setText(statusText);
        } else {
            // 如果有搜索文本，显示搜索状态
            statusLabel.setText("搜索 '" + searchText + "' - 找到 " + filteredCount + "/" + totalCount + " 个项目");
        }
    }
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";
    }

    /**
     * 更新状态栏的搜索信息
     */
    private void updateStatusWithSearchInfo() {
        String searchText = searchField.getText().trim();
        int totalCount = allFileItems.size();
        int filteredCount = filteredFileItems.size();

        if (searchText.isEmpty()) {
            statusLabel.setText("显示 " + totalCount + " 个项目");
        } else {
            statusLabel.setText("搜索 '" + searchText + "' - 找到 " + filteredCount + "/" + totalCount + " 个项目");
        }
    }

    /**
     * 清除搜索
     */
//    @FXML
//    private void clearSearch() {
//        searchField.clear();
//        searchTypeCombo.getSelectionModel().selectFirst();
//        caseSensitiveCheckBox.setSelected(false);
//
//        // 重置搜索过滤器
//        filteredFileItems.setPredicate(item -> true);
//        updateStatusWithSearchInfo();
//
//        // 刷新视图
//        fileTable.refresh();
//    }

    /**
     * 快速搜索（从键盘快捷键调用）
     */
    private void focusSearchField() {
        if (searchField != null) {
            searchField.requestFocus();
            searchField.selectAll();
        }
    }
    /**
     * 设置文件表格为多选模式
     */
    /**
     * 设置文件表格为多选模式
     */
    /**
 * 设置文件表格为多选模式 - 修复冲突
 */
/**
 * 设置文件表格为多选模式 - 修复冲突
 */
//private void setupMultipleSelection() {
//    // 设置表格为多选模式
//    fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
//
//    // 处理键盘事件
//    fileTable.setOnKeyPressed(event -> {
//        if (event.isControlDown()) {
//            switch (event.getCode()) {
//                case C -> {
//                    copySelectedItems();
//                    event.consume();
//                }
//                case X -> {
//                    cutSelectedItems();
//                    event.consume();
//                }
//                case V -> {
//                    pasteItems();
//                    event.consume();
//                }
//                case A -> {
//                    fileTable.getSelectionModel().selectAll();
//                    event.consume();
//                }
//            }
//        } else {
//            switch (event.getCode()) {
//                case DELETE -> {
//                    deleteSelectedItems();
//                    event.consume();
//                }
//                case F2 -> {
//                    renameSelectedItem();
//                    event.consume();
//                }
//            }
//        }
//    });
//
//    // 仅处理键盘事件，鼠标事件由 setupTableRowFactory 处理
//}

/**
 * 设置表格行工厂 - 统一处理鼠标事件
 */
/**
 * 设置表格行工厂 - 修复Control键多选
 */
    /**
     * 完全重写的表格行工厂，确保Control键正常工作
     */
    /**
     * 完全重写的表格行工厂，确保Control键正常工作
     */
    /**
     * 设置表格行工厂 - 修复版本
     * 只处理双击事件，让JavaFX默认处理选择和Control键
     */
    private void setupTableRowFactory() {
        fileTable.setRowFactory(tv -> {
            TableRow<FileItem> row = new TableRow<>();

            row.setOnMouseClicked(event -> {
                if (row.isEmpty()) {
                    return;
                }

                FileItem item = row.getItem();
                if (item == null) {
                    return;
                }

                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    // 双击：打开文件夹或文件
                    handleDoubleClick(item);
                    event.consume();
                } else if (event.getClickCount() == 1) {
                    // 单击：如果是文件夹，显示详细信息
                    if (item.isDirectory()) {
                        // 延迟一点执行，避免与选择事件冲突
                        Platform.runLater(() -> {
                            showSelectedFolderDetails(item);
                        });
                    } else {
                        // 如果是文件，在状态栏显示文件信息
                        showSelectedFileDetails(item);
                    }
                }
            });

            return row;
        });
    }
    /**
     * 显示选中的文件夹详细信息
     */
    private void showSelectedFolderDetails(FileItem folderItem) {
        if (folderItem == null || !folderItem.isDirectory()) {
            return;
        }

        Path folderPath = folderItem.getPath();
        String folderName = folderItem.getName();

        // 在后台线程中获取详细信息
        new Thread(() -> {
            try {
                // 获取文件夹基本信息
                BasicFileAttributes attrs = Files.readAttributes(folderPath, BasicFileAttributes.class);

                // 统计文件夹内容
                int fileCount = 0;
                int subFolderCount = 0;
                long totalSize = 0;

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(folderPath)) {
                    for (Path entry : stream) {
                        if (Files.isDirectory(entry)) {
                            subFolderCount++;
                        } else {
                            fileCount++;
                            try {
                                totalSize += Files.size(entry);
                            } catch (Exception e) {
                                // 忽略无法获取大小的文件
                            }
                        }
                    }
                } catch (Exception e) {
                    // 如果无法访问目录内容
                }

                // 格式化时间
                String modifyTime = "";
                if (attrs.lastModifiedTime() != null) {
                    LocalDateTime time = LocalDateTime.ofInstant(
                            attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                    modifyTime = time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }

                // 获取权限信息
                String permissions = getPermissionsString(folderPath);

                final int finalFileCount = fileCount;
                final int finalSubFolderCount = subFolderCount;
                final long finalTotalSize = totalSize;
                final String finalModifyTime = modifyTime;
                final String finalPermissions = permissions;

                Platform.runLater(() -> {
                    // 在状态栏显示详细信息
                    String statusText = String.format("选中文件夹: %s | 大小: %s | 文件: %d | 子文件夹: %d | 修改时间: %s",
                            folderName,
                            formatFileSize(finalTotalSize),
                            finalFileCount,
                            finalSubFolderCount,
                            finalModifyTime);

                    statusLabel.setText(statusText);

                    // 添加工具提示显示更多信息
                    StringBuilder tooltipText = new StringBuilder();
                    tooltipText.append("文件夹详细信息\n");
                    tooltipText.append("===============\n");
                    tooltipText.append("名称: ").append(folderName).append("\n");
                    tooltipText.append("路径: ").append(folderPath).append("\n");
                    tooltipText.append("大小: ").append(formatFileSize(finalTotalSize)).append("\n");
                    tooltipText.append("文件数量: ").append(finalFileCount).append("\n");
                    tooltipText.append("子文件夹数量: ").append(finalSubFolderCount).append("\n");
                    tooltipText.append("总计项目: ").append(finalFileCount + finalSubFolderCount).append("\n");
                    tooltipText.append("修改时间: ").append(finalModifyTime).append("\n");
                    tooltipText.append("权限: ").append(finalPermissions).append("\n");

                    Tooltip tooltip = new Tooltip(tooltipText.toString());
                    tooltip.setShowDelay(javafx.util.Duration.millis(500));
                    statusLabel.setTooltip(tooltip);

                    // 也可以更新路径框显示当前选中的文件夹路径
                    // 但不进行导航
                    // pathField.setText(folderPath.toString());
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("无法获取文件夹信息: " + e.getMessage());
                });
            }
        }).start();
    }
    /**
     * 显示选中的文件详细信息
     */
    private void showSelectedFileDetails(FileItem fileItem) {
        if (fileItem == null || fileItem.isDirectory()) {
            return;
        }

        Path filePath = fileItem.getPath();
        String fileName = fileItem.getName();
        long fileSize = fileItem.getSize();

        new Thread(() -> {
            try {
                BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);

                // 格式化时间
                String modifyTime = "";
                if (attrs.lastModifiedTime() != null) {
                    LocalDateTime time = LocalDateTime.ofInstant(
                            attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                    modifyTime = time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }

                // 获取权限
                String permissions = getPermissionsString(filePath);

                // 获取文件类型
                String fileType = getFileTypeDescription(fileName);

                final String finalModifyTime = modifyTime;
                final String finalPermissions = permissions;
                final String finalFileType = fileType;

                Platform.runLater(() -> {
                    String statusText = String.format("选中文件: %s | 大小: %s | 类型: %s | 修改时间: %s",
                            fileName,
                            formatFileSize(fileSize),
                            finalFileType,
                            finalModifyTime);

                    statusLabel.setText(statusText);

                    // 工具提示
                    StringBuilder tooltipText = new StringBuilder();
                    tooltipText.append("文件详细信息\n");
                    tooltipText.append("============\n");
                    tooltipText.append("名称: ").append(fileName).append("\n");
                    tooltipText.append("路径: ").append(filePath).append("\n");
                    tooltipText.append("大小: ").append(formatFileSize(fileSize)).append("\n");
                    tooltipText.append("类型: ").append(finalFileType).append("\n");
                    tooltipText.append("修改时间: ").append(finalModifyTime).append("\n");
                    tooltipText.append("权限: ").append(finalPermissions).append("\n");

                    Tooltip tooltip = new Tooltip(tooltipText.toString());
                    tooltip.setShowDelay(javafx.util.Duration.millis(500));
                    statusLabel.setTooltip(tooltip);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("文件: " + fileName + " | 大小: " + formatFileSize(fileSize));
                });
            }
        }).start();
    }
    /**
     * 获取权限字符串
     */
    private String getPermissionsString(Path path) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            return PosixFilePermissions.toString(perms);
        } catch (UnsupportedOperationException e) {
            // 非POSIX系统，使用基本权限
            StringBuilder perm = new StringBuilder();
            perm.append(Files.isReadable(path) ? "r" : "-");
            perm.append(Files.isWritable(path) ? "w" : "-");
            perm.append(Files.isExecutable(path) ? "x" : "-");
            return perm.toString();
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 获取文件类型描述
     */
    private String getFileTypeDescription(String fileName) {
        if (fileName == null) {
            return "未知";
        }

        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")) {
            return "文本文档";
        } else if (lowerName.endsWith(".java") || lowerName.endsWith(".py") || lowerName.endsWith(".cpp")) {
            return "源代码文件";
        } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".png") || lowerName.endsWith(".gif")) {
            return "图片文件";
        } else if (lowerName.endsWith(".pdf") || lowerName.endsWith(".doc") || lowerName.endsWith(".docx")) {
            return "文档";
        } else if (lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z")) {
            return "压缩文件";
        } else if (lowerName.endsWith(".exe") || lowerName.endsWith(".bat") || lowerName.endsWith(".sh")) {
            return "可执行文件";
        } else {
            return "文件";
        }
    }

    /**
     * 格式化文件大小
     */


/**
 * 获取最后选中的索引
 */
//private int getLastSelectedIndex() {
//    ObservableList<Integer> selectedIndices = fileTable.getSelectionModel().getSelectedIndices();
//    if (!selectedIndices.isEmpty()) {
//        return selectedIndices.get(selectedIndices.size() - 1);
//    }
//    return -1;
//}





    /**
 * 设置表格行工厂
 */
/**
 * 设置表格行工厂
 */
/**
 * 设置表格行工厂 - 避免双击事件冲突
 */





    /**
     * 获取指定位置的表格行
     */
    private TableRow<FileItem> getRowAtPosition(double x, double y) {
        // 获取点击位置的节点
        Node node = fileTable.getChildrenUnmodifiable().stream()
                .filter(n -> n.contains(n.parentToLocal(x, y)))
                .findFirst()
                .orElse(null);

        // 查找TableRow
        while (node != null && !(node instanceof TableRow)) {
            node = node.getParent();
        }

        return (TableRow<FileItem>) node;
    }

    /**
     * 处理多选逻辑
     */
//    private void handleMultiSelect(int clickedIndex, MouseEvent event) {
//        MultipleSelectionModel<FileItem> selectionModel = fileTable.getSelectionModel();
//
//        if (event.isControlDown()) {
//            // Ctrl+点击：切换选择状态
//            if (selectionModel.isSelected(clickedIndex)) {
//                selectionModel.clearSelection(clickedIndex);
//            } else {
//                selectionModel.select(clickedIndex);
//            }
//        } else if (event.isShiftDown()) {
//            // Shift+点击：选择连续范围
//            int lastSelected = getLastSelectedIndex();
//            if (lastSelected >= 0) {
//                int start = Math.min(lastSelected, clickedIndex);
//                int end = Math.max(lastSelected, clickedIndex);
//                selectionModel.clearSelection();
//                selectionModel.selectRange(start, end + 1);
//            } else {
//                selectionModel.select(clickedIndex);
//            }
//        } else {
//            // 普通点击：单选
//            selectionModel.clearSelection();
//            selectionModel.select(clickedIndex);
//        }
//    }

    /**
     * 获取最后选中的索引
     */

    /**
     * 更新粘贴菜单项状态
     */
    /**
 * 更新粘贴菜单项状态
 */
    private void updatePasteMenuItem() {
        boolean enabled = clipboardHasItems && !clipboardItems.isEmpty();
        if (pasteMenuItem != null) {
            pasteMenuItem.setDisable(!enabled);
        }
    }



    /**
     * 复制选中的项目到剪贴板
     */
    @FXML
    private void copySelectedItems() {
        try {
            ObservableList<FileItem> selectedItems = fileTable.getSelectionModel().getSelectedItems();
            if (selectedItems.isEmpty()) {
                showInfo("请先选择要复制的文件或文件夹");
                return;
            }

            // 将所有选中项目添加到剪贴板
            clipboardItems.clear();
            clipboardItems.addAll(selectedItems);
            clipboardOperation = ClipboardOperation.COPY;
            clipboardHasItems = true;

            Platform.runLater(() -> {
                if (progressBar != null) {
                    progressBar.setVisible(true);
                    progressBar.setProgress(0);
                }
                statusLabel.setText("已复制 " + selectedItems.size() + " 个项目到剪贴板");
            });

            // 模拟复制进度
            new Thread(() -> {
                try {
                    int total = selectedItems.size();
                    for (int i = 0; i < total; i++) {
                        double progress = (double) (i + 1) / total;
                        Platform.runLater(() -> {
                            if (progressBar != null) {
                                progressBar.setProgress(progress);
                            }
                        });
                        Thread.sleep(50); // 模拟处理时间
                    }

                    Platform.runLater(() -> {
                        if (progressBar != null) {
                            progressBar.setVisible(false);
                        }
                        statusLabel.setText("已复制 " + total + " 个项目到剪贴板");

                        // 更新粘贴菜单项状态
                        updatePasteMenuItem();
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

        } catch (Exception e) {
            System.err.println("复制到剪贴板失败: " + e.getMessage());
            showError("复制失败: " + e.getMessage());
        }
    }


    /**
     * 复制项目
     */
    private boolean copyItem(Path source, Path targetDir) {
        try {
            Path target = targetDir.resolve(source.getFileName());

            // 处理同名文件
            if (Files.exists(target)) {
                if (!handleDuplicateFile(target)) {
                    return false; // 用户选择跳过
                }
            }

            if (Files.isDirectory(source)) {
                // 复制文件夹
                copyDirectory(source, target);
            } else {
                // 复制文件
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }

            System.out.println("复制: " + source + " -> " + target);
            return true;

        } catch (Exception e) {
            System.err.println("复制失败: " + source + " -> " + e.getMessage());
            return false;
        }
    }

    /**
     * 移动项目
     */
    private boolean moveItem(Path source, Path targetDir) {
        try {
            Path target = targetDir.resolve(source.getFileName());

            // 处理同名文件
            if (Files.exists(target)) {
                if (!handleDuplicateFile(target)) {
                    return false; // 用户选择跳过
                }
            }

            if (Files.isDirectory(source)) {
                // 移动文件夹
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                // 移动文件
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }

            System.out.println("移动: " + source + " -> " + target);
            return true;

        } catch (Exception e) {
            System.err.println("移动失败: " + source + " -> " + e.getMessage());
            return false;
        }
    }

    /**
     * 处理重复文件
     */
    private boolean handleDuplicateFile(Path target) {
        // 暂时使用覆盖策略
        // 后期可以扩展为让用户选择
        return true;
    }

    /**
     * 递归复制文件夹
     */
    private void copyDirectory(Path source, Path target) throws IOException {
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
            } catch (Exception e) {
                System.err.println("复制子项失败: " + sourcePath + " -> " + e.getMessage());
            }
        });
    }

    /**
     * 剪切选中的项目
     */
    /**
     * 剪切选中的项目到剪贴板
     */
    private void cutSelectedItems() {
        try {
            ObservableList<FileItem> selectedItems = fileTable.getSelectionModel().getSelectedItems();
            if (selectedItems.isEmpty()) {
                showInfo("请先选择要剪切的文件或文件夹");
                return;
            }

            // 将所有选中项目添加到剪贴板
            clipboardItems.clear();
            clipboardItems.addAll(selectedItems);
            clipboardOperation = ClipboardOperation.CUT;
            clipboardHasItems = true;

            Platform.runLater(() -> {
                if (progressBar != null) {
                    progressBar.setVisible(true);
                    progressBar.setProgress(0);
                }
                statusLabel.setText("已剪切 " + selectedItems.size() + " 个项目");
            });

            // 模拟剪切进度
            new Thread(() -> {
                try {
                    int total = selectedItems.size();
                    for (int i = 0; i < total; i++) {
                        double progress = (double) (i + 1) / total;
                        Platform.runLater(() -> {
                            if (progressBar != null) {
                                progressBar.setProgress(progress);
                            }
                        });
                        Thread.sleep(50);
                    }

                    Platform.runLater(() -> {
                        if (progressBar != null) {
                            progressBar.setVisible(false);
                        }
                        statusLabel.setText("已剪切 " + total + " 个项目");

                        // 更新粘贴菜单项状态
                        updatePasteMenuItem();
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

        } catch (Exception e) {
            System.err.println("剪切到剪贴板失败: " + e.getMessage());
            showError("剪切失败: " + e.getMessage());
        }
    }


    /**
 * 粘贴项目
 */
/**
 * 粘贴项目
 */
@FXML
private void pasteItems() {
    try {
        if (!clipboardHasItems || clipboardItems.isEmpty()) {
            showInfo("剪贴板为空");
            return;
        }

        // 获取目标路径
        Path targetPath = currentPath;
        if (targetPath == null) {
            showError("无法确定粘贴位置");
            return;
        }

        // 确认对话框
        String operationName = clipboardOperation == ClipboardOperation.COPY ? "复制" : "移动";
        Alert confirmDialog = new Alert(
                Alert.AlertType.CONFIRMATION,
                "确定要" + operationName + " " + clipboardItems.size() + " 个项目到此处吗？",
                ButtonType.YES, ButtonType.NO
        );
        confirmDialog.setTitle("确认" + operationName);
        confirmDialog.setHeaderText(operationName + "确认");

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            System.out.println("开始" + operationName + "操作，项目数量: " + clipboardItems.size());

            // 显示进度条
            Platform.runLater(() -> {
                if (progressBar != null) {
                    progressBar.setVisible(true);
                    progressBar.setProgress(0);
                }
            });

            // 在后台线程执行粘贴操作
            new Thread(() -> {
                try {
                    int successCount = 0;
                    int failCount = 0;
                    int total = clipboardItems.size();

                    for (int i = 0; i < total; i++) {
                        FileItem item = clipboardItems.get(i);
                        Path source = item.getPath();
                        Path target = targetPath.resolve(source.getFileName());

                        // 如果目标已存在，添加数字后缀
                        target = getUniqueTargetPath(target);

                        // 在状态栏显示进度
                        double progress = (double) (i + 1) / total;
                        int percent = (int)(progress * 100);
                        String progressText = operationName + "中: " + (i+1) + "/" + total +
                                " (" + percent + "%) - " + item.getName();

                        // 在UI线程更新进度条和状态栏
                        Platform.runLater(() -> {
                            if (progressBar != null) {
                                progressBar.setProgress(progress);
                            }
                            statusLabel.setText(progressText);
                        });

                        boolean success = false;
                        int finalI = i;
                        Consumer<Double> fileProgressCallback = fileProgress -> {
                            // 计算总体进度
                            double overallProgress = ((double) finalI + fileProgress) / total;
                            Platform.runLater(() -> {
                                if (progressBar != null) {
                                    progressBar.setProgress(overallProgress);
                                }
                                int overallPercent = (int)(overallProgress * 100);
                                String text = operationName + "中: " + (finalI +1) + "/" + total +
                                        " (" + overallPercent + "%)";
                                statusLabel.setText(text);
                            });
                        };

                        if (clipboardOperation == ClipboardOperation.COPY) {
                            // 使用带进度的复制方法
                            success = FileOperationService.copyFileWithProgress(source, target, fileProgressCallback);
                        } else { // CUT
                            // 使用带进度的移动方法
                            success = FileOperationService.moveFileWithProgress(source, target, fileProgressCallback);
                        }

                        if (success) {
                            successCount++;
                        } else {
                            failCount++;
                        }
                    }

                    // 操作完成后的UI更新
                    int finalSuccessCount = successCount;
                    int finalFailCount = failCount;
                    Platform.runLater(() -> {
                        String message = operationName + "完成: 成功 " + finalSuccessCount + " 个, 失败 " + finalFailCount + " 个";
                        System.out.println(message);

                        // 完成时设置进度为100%
                        if (progressBar != null) {
                            progressBar.setProgress(1.0);
                        }

                        statusLabel.setText(message);

                        // 如果是剪切操作，清空剪贴板
                        if (clipboardOperation == ClipboardOperation.CUT) {
                            clipboardItems.clear();
                            clipboardHasItems = false;
                            // 更新粘贴菜单项状态
                            updatePasteMenuItem();
                        }

                        // 刷新目录
                        refreshDirectory();

                        // 隐藏进度条
                        if (progressBar != null) {
                            progressBar.setVisible(false);
                        }
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        System.err.println("粘贴失败: " + e.getMessage());
                        showError("粘贴失败: " + e.getMessage());

                        if (progressBar != null) {
                            progressBar.setVisible(false);
                        }
                    });
                }
            }).start();
        } else {
            System.out.println("用户取消了粘贴操作");
        }

    } catch (Exception e) {
        System.err.println("粘贴异常: " + e.getMessage());
        showError("粘贴时发生错误: " + e.getMessage());
    }
}

    // 辅助方法：获取唯一的文件路径
    private Path getUniqueTargetPath(Path originalPath) {
        if (!Files.exists(originalPath)) {
            return originalPath;
        }

        Path parent = originalPath.getParent();
        String fileName = originalPath.getFileName().toString();
        String nameWithoutExt = fileName;
        String extension = "";

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        int counter = 1;
        Path newPath;
        do {
            String newFileName = nameWithoutExt + "(" + counter + ")" + extension;
            newPath = parent.resolve(newFileName);
            counter++;
        } while (Files.exists(newPath));

        return newPath;
    }



    private long getFileSize(Path path) {
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
                });
                return size[0];
            } else {
                // 文件大小
                return Files.size(path);
            }
        } catch (IOException e) {
            System.err.println("获取文件大小失败: " + e.getMessage());
            return 0;
        }
    }
    /**
     * 显示信息对话框
     */
    private void showInfo(String message) {
        statusLabel.setText("信息: " + message);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }
    /**
     * 设置键盘快捷键 - 不干扰选择
     */
    private void setupKeyboardShortcuts() {
        // 只在表格有焦点时处理键盘事件
        fileTable.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case C -> {
                        copySelectedItems();
                        event.consume();
                    }
                    case X -> {
                        cutSelectedItems();
                        event.consume();
                    }
                    case V -> {
                        pasteItems();
                        event.consume();
                    }
                    case A -> {
                        fileTable.getSelectionModel().selectAll();
                        event.consume();
                    }
                }
            } else {
                switch (event.getCode()) {
                    case DELETE -> {
                        deleteSelectedItems();
                        event.consume();
                    }
                    case F2 -> {
                        renameSelectedItem();
                        event.consume();
                    }
                    case ENTER -> {
                        // 打开选中的项目
                        openSelectedItem();
                        event.consume();
                    }
                }
            }
        });
    }

    /**
     * 打开文件表格中选中的项目
     */
    private void openSelectedItem() {
        FileItem selectedItem = fileTable.getSelectionModel().getSelectedItem();
        if (selectedItem != null && selectedItem.isDirectory()) {
            String folderPath = selectedItem.getPath().toAbsolutePath().normalize().toString();
            currentPath = Paths.get(folderPath);
            navigateToFolder(folderPath);
        }
    }

    /**
     * 打开目录树中选中的项目
     */
    private void openSelectedTreeItem() {
        TreeItem<String> selectedItem = directoryTree.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            String path = extractPathFromTreeItem(selectedItem);
            if (path != null) {
                currentPath = Paths.get(path).toAbsolutePath().normalize();
                navigateToFolder(path);
            }
        }
    }
    private boolean isFocusOnTextField() {
        Node focusOwner = mainPane.getScene().getFocusOwner();
        return focusOwner instanceof TextInputControl;
    }

    /**
     * 处理文件表格键盘事件
     */
    private void handleFileTableKeyEvent(KeyEvent event) {
        if (event.isControlDown()) {
            switch (event.getCode()) {
                case A -> {  // Ctrl+A 全选
                    fileTable.getSelectionModel().selectAll();
                    event.consume();
                }
                case C -> copySelectedItems();
                case X -> cutSelectedItems();
                case V -> pasteItems();
                default -> {}
            }
        } else {
            switch (event.getCode()) {
                case F2 -> renameSelectedItem();
                case F5 -> refreshDirectory();
                case DELETE -> deleteSelectedItems();
                case ENTER -> openSelectedItem();
                default -> {}
            }
        }
    }
    /**
     * 初始化右键菜单
     */
   /**
 * 初始化右键菜单
 */
//private void initContextMenus() {
//    try {
//        System.out.println("初始化右键菜单...");
//
//        // === 1. 创建菜单项 ===
//        if (newFolderMenuItem == null) {
//            newFolderMenuItem = new MenuItem("新建文件夹");
//            newFolderMenuItem.setOnAction(e -> createNewFolder());
//        }
//
//        if (newFileMenuItem == null) {
//            newFileMenuItem = new MenuItem("新建文件");
//            newFileMenuItem.setOnAction(e -> createNewFile());
//        }
//
//        if (renameMenuItem == null) {
//            renameMenuItem = new MenuItem("重命名");
//            renameMenuItem.setOnAction(e -> renameSelectedItem());
//        }
//
//        if (deleteMenuItem == null) {
//            deleteMenuItem = new MenuItem("删除");
//            deleteMenuItem.setOnAction(e -> deleteSelectedItems());
//        }
//
//        if (refreshMenuItem == null) {
//            refreshMenuItem = new MenuItem("刷新");
//            refreshMenuItem.setOnAction(e -> refreshDirectory());
//        }
//
//        if (copyMenuItem == null) {
//            copyMenuItem = new MenuItem("复制");
//            copyMenuItem.setOnAction(e -> copySelectedItems());
//        }
//
//        if (cutMenuItem == null) {
//            cutMenuItem = new MenuItem("剪切");
//            cutMenuItem.setOnAction(e -> cutSelectedItems());
//        }
//
//        if (pasteMenuItem == null) {
//            pasteMenuItem = new MenuItem("粘贴");
//            pasteMenuItem.setOnAction(e -> pasteItems());
//        }
//
//        // 初始禁用粘贴项
//        updatePasteMenuItem();
//
//        // === 2. 创建目录树右键菜单 ===
//        if (directoryTreeContextMenu == null) {
//            directoryTreeContextMenu = new ContextMenu();
//        }
//        directoryTreeContextMenu.getItems().setAll(
//                newFolderMenuItem,
//                newFileMenuItem,
//                new SeparatorMenuItem(),
//                renameMenuItem,
//                deleteMenuItem,
//                new SeparatorMenuItem(),
//                copyMenuItem,
//                cutMenuItem,
//                pasteMenuItem,
//                new SeparatorMenuItem(),
//                refreshMenuItem
//        );
//
//        // === 3. 创建文件表格右键菜单 ===
//        if (fileTableContextMenu == null) {
//            fileTableContextMenu = new ContextMenu();
//        }
//        fileTableContextMenu.getItems().setAll(
//                copyMenuItem,
//                cutMenuItem,
//                pasteMenuItem,
//                new SeparatorMenuItem(),
//                renameMenuItem,
//                deleteMenuItem,
//                new SeparatorMenuItem(),
//                refreshMenuItem
//        );
//
//        // === 4. 设置右键菜单 ===
//        directoryTree.setContextMenu(directoryTreeContextMenu);
//        fileTable.setContextMenu(fileTableContextMenu);
//
//        // === 5. 为文件表格设置行工厂，支持右键点击选中 ===
//        setupTableRowFactory();
//
//        System.out.println("右键菜单初始化完成");
//
//    } catch (Exception e) {
//        System.err.println("初始化右键菜单失败: " + e.getMessage());
//        e.printStackTrace();
//    }
//}
    /**
     * 初始化右键菜单
     */
    private void initContextMenus() {
        // 表格右键菜单
        ContextMenu tableContextMenu = new ContextMenu();

        MenuItem open = new MenuItem("打开");
        open.setOnAction(e -> openSelectedItem());

        MenuItem copy = new MenuItem("复制");
        copy.setOnAction(e -> copySelectedItems());

        MenuItem paste = new MenuItem("粘贴");
        paste.setOnAction(e -> pasteItems());

        MenuItem delete = new MenuItem("删除");
        delete.setOnAction(e -> deleteSelectedItems());

        MenuItem rename = new MenuItem("重命名");
        rename.setOnAction(e -> renameSelectedItem());

        tableContextMenu.getItems().addAll(open, copy, paste, delete, rename);

        // 为表格设置右键菜单
        fileTable.setContextMenu(tableContextMenu);

        // 右键点击时确保选中正确的行
        fileTable.setOnContextMenuRequested(event -> {
            // 获取点击的行
            int rowIndex = fileTable.getSelectionModel().getSelectedIndex();

            // 如果没有选中任何行，并且点击了表格的某一行
            if (rowIndex == -1) {
                // 尝试根据鼠标位置找到行
                Point2D point = fileTable.screenToLocal(event.getScreenX(), event.getScreenY());
                rowIndex = (int) (point.getY() / 24); // 24是行的大致高度

                if (rowIndex >= 0 && rowIndex < fileTable.getItems().size()) {
                    fileTable.getSelectionModel().select(rowIndex);
                }
            }
        });
    }

    /**
     * 设置表格行工厂，支持右键点击选中
     */
    /**
     * 设置表格行工厂，支持右键点击选中
     */
    /**
     * 创建新文件夹
     */
    @FXML
    private void createNewFolder() {
        try {
            System.out.println("创建新文件夹...");

            // 获取目标路径
            Path targetPath = getTargetPathForNewItem();
            if (targetPath == null) {
                showError("无法确定创建位置");
                return;
            }

            // 生成默认文件夹名
            String baseName = "新建文件夹";
            String folderName = baseName;
            int counter = 1;

            while (Files.exists(targetPath.resolve(folderName))) {
                folderName = baseName + " (" + counter + ")";
                counter++;
            }

            // 创建文件夹
            Path newFolderPath = targetPath.resolve(folderName);
            Files.createDirectories(newFolderPath);

            System.out.println("创建文件夹: " + newFolderPath);
            statusLabel.setText("已创建文件夹: " + folderName);

            // 刷新目录显示
            refreshDirectory();

            // 在文件表格中选中新创建的文件夹
            selectNewItemInTable(folderName);

        } catch (AccessDeniedException e) {
            System.err.println("无权限创建文件夹: " + e.getMessage());
            showError("无权限在此位置创建文件夹");
        } catch (IOException e) {
            System.err.println("创建文件夹失败: " + e.getMessage());
            showError("创建文件夹失败: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("创建文件夹异常: " + e.getMessage());
            e.printStackTrace();
            showError("创建文件夹时发生错误");
        }
    }
    /**
     * 获取新项目的目标路径
     */
    private Path getTargetPathForNewItem() {
        // 如果文件表格有选中项，使用其路径
        FileItem selectedFile = fileTable.getSelectionModel().getSelectedItem();
        if (selectedFile != null) {
            if (selectedFile.isDirectory()) {
                return selectedFile.getPath();
            } else {
                return selectedFile.getPath().getParent();
            }
        }

        // 否则使用当前路径
        return currentPath;
    }

    /**
     * 在文件表格中选中新创建的项目
     */
    private void selectNewItemInTable(String itemName) {
        Platform.runLater(() -> {
            try {
                // 等待一下，确保数据已刷新
                Thread.sleep(100);

                // 查找并选中新项目
                for (FileItem item : fileTable.getItems()) {
                    if (item.getName().equals(itemName)) {
                        fileTable.getSelectionModel().select(item);
                        fileTable.scrollTo(item);
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("选中新项目失败: " + e.getMessage());
            }
        });
    }
    /**
     * 隐藏进度条
     */
    private void hideProgressBar() {
        if (progressBar != null) {
            // 延迟3秒后隐藏进度条
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    Platform.runLater(() -> {
                        progressBar.setVisible(false);
                        progressBar.setProgress(0);
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
    /**
     * 创建新文件
     */
    private void createNewFile() {
        try {
            System.out.println("创建新文件...");

            // 获取目标路径
            Path targetPath = getTargetPathForNewItem();
            if (targetPath == null) {
                showError("无法确定创建位置");
                return;
            }

            // 弹窗让用户输入文件名
            TextInputDialog dialog = new TextInputDialog("新建文件.txt");
            dialog.setTitle("新建文件");
            dialog.setHeaderText("请输入文件名:");
            dialog.setContentText("文件名:");

            Optional<String> result = dialog.showAndWait();
            if (result.isPresent() && !result.get().trim().isEmpty()) {
                String fileName = result.get().trim();

                // 如果文件名没有扩展名，添加.txt
                if (!fileName.contains(".")) {
                    fileName += ".txt";
                }

                // 检查文件是否已存在
                Path newFilePath = targetPath.resolve(fileName);
                if (Files.exists(newFilePath)) {
                    showError("文件已存在: " + fileName);
                    return;
                }

                // 创建文件
                Files.createFile(newFilePath);

                System.out.println("创建文件: " + newFilePath);
                statusLabel.setText("已创建文件: " + fileName);

                // 刷新目录显示
                refreshDirectory();

                // 在文件表格中选中新创建的文件
                selectNewItemInTable(fileName);

            } else {
                System.out.println("用户取消了文件创建");
            }

        } catch (AccessDeniedException e) {
            System.err.println("无权限创建文件: " + e.getMessage());
            showError("无权限在此位置创建文件");
        } catch (IOException e) {
            System.err.println("创建文件失败: " + e.getMessage());
            showError("创建文件失败: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("创建文件异常: " + e.getMessage());
            e.printStackTrace();
            showError("创建文件时发生错误");
        }
    }
    /**
     * 删除选中的项目
     */
    /**
     * 删除选中的项目
     */
    @FXML
private void deleteSelectedItems() {
    try {
        ObservableList<FileItem> selectedItems = fileTable.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) {
            showInfo("请先选择要删除的文件或文件夹");
            return;
        }

        // 确认对话框
        Alert confirmDialog = new Alert(
                Alert.AlertType.CONFIRMATION,
                "确定要删除 " + selectedItems.size() + " 个项目吗？此操作不可恢复！"
        );
        confirmDialog.setTitle("确认删除");
        Optional<ButtonType> result = confirmDialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            // 显示进度
            Platform.runLater(() -> {
                if (progressBar != null) {
                    progressBar.setVisible(true);
                    progressBar.setProgress(0);
                }
                statusLabel.setText("正在删除 " + selectedItems.size() + " 个项目...");
            });

            // 执行删除操作
            new Thread(() -> {
                try {
                    int successCount = 0;
                    int totalCount = selectedItems.size();

                    for (int i = 0; i < totalCount; i++) {
                        FileItem item = selectedItems.get(i);
                        Path path = item.getPath();

                        int finalI = i;
                        boolean success = FileOperationService.deleteWithProgress(path,
                            progress -> Platform.runLater(() -> {
                                if (progressBar != null) {
                                    // 计算总体进度
                                    double overallProgress = ((double) finalI + progress) / totalCount;
                                    progressBar.setProgress(overallProgress);
                                }
                            }));

                        if (success) {
                            successCount++;
                        }

                        // 更新状态
                        int finalSuccessCount = successCount;
                        Platform.runLater(() -> {
                            statusLabel.setText("已删除 " + finalSuccessCount + "/" + totalCount + " 个项目");
                        });
                    }

                    int finalSuccessCount1 = successCount;
                    Platform.runLater(() -> {
                        if (progressBar != null) {
                            progressBar.setVisible(false);
                        }
                        statusLabel.setText("删除完成: " + finalSuccessCount1 + "/" + totalCount + " 个项目");

                        // 刷新目录
                        refreshDirectory();
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        if (progressBar != null) {
                            progressBar.setVisible(false);
                        }
                        showError("删除失败: " + e.getMessage());
                    });
                }
            }).start();
        }

    } catch (Exception e) {
        System.err.println("删除操作失败: " + e.getMessage());
        showError("删除失败: " + e.getMessage());
    }
}

    /**
     * 删除文件
     */
    private void deleteFile(Path filePath) throws IOException {
        try {
            // 尝试移动到回收站（Windows系统）
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                if (moveToRecycleBin(filePath)) {
                    System.out.println("文件已移动到回收站: " + filePath);
                    return;
                }
            }

            // 如果无法移动到回收站，则直接删除
            Files.deleteIfExists(filePath);
            System.out.println("文件已删除: " + filePath);

        } catch (AccessDeniedException e) {
            throw new IOException("无权限删除文件: " + filePath.getFileName(), e);
        }
    }

    /**
     * 删除文件夹
     */
    private void deleteDirectory(Path dirPath) throws IOException {
        try {
            // 尝试移动到回收站（Windows系统）
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                if (moveToRecycleBin(dirPath)) {
                    System.out.println("文件夹已移动到回收站: " + dirPath);
                    return;
                }
            }

            // 如果无法移动到回收站，则递归删除
            deleteRecursive(dirPath);
            System.out.println("文件夹已删除: " + dirPath);

        } catch (AccessDeniedException e) {
            throw new IOException("无权限删除文件夹: " + dirPath.getFileName(), e);
        }
    }

    /**
     * 递归删除文件夹
     */
    private void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var dirStream = Files.newDirectoryStream(path)) {
                for (Path child : dirStream) {
                    deleteRecursive(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    /**
     * 移动到回收站（Windows系统）
     */
    private boolean moveToRecycleBin(Path path) {
        try {
            File file = path.toFile();

            // 使用Java AWT的FileSystemView尝试移动到回收站
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            if (desktop.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)) {
                return desktop.moveToTrash(file);
            }

            return false;
        } catch (Exception e) {
            System.err.println("移动到回收站失败: " + e.getMessage());
            return false;
        }
    }
    /**
     * 重命名选中的项目
     */
    private void renameSelectedItem() {
        try {
            // 获取选中的文件/文件夹
            FileItem selectedItem = fileTable.getSelectionModel().getSelectedItem();
            if (selectedItem == null) {
                showError("请先选择一个文件或文件夹");
                return;
            }

            // 弹窗让用户输入新名称
            TextInputDialog dialog = new TextInputDialog(selectedItem.getName());
            dialog.setTitle("重命名");
            dialog.setHeaderText("请输入新的名称:");
            dialog.setContentText("名称:");

            Optional<String> result = dialog.showAndWait();
            if (result.isPresent() && !result.get().trim().isEmpty()) {
                String newName = result.get().trim();

                // 检查新名称是否有效
                if (newName.equals(selectedItem.getName())) {
                    System.out.println("名称未改变");
                    return;
                }

                if (newName.contains("/") || newName.contains("\\") ||
                        newName.contains(":") || newName.contains("*") ||
                        newName.contains("?") || newName.contains("\"") ||
                        newName.contains("<") || newName.contains(">") ||
                        newName.contains("|")) {
                    showError("文件名包含非法字符");
                    return;
                }

                // 获取原路径和新路径
                Path oldPath = selectedItem.getPath();
                Path newPath = oldPath.getParent().resolve(newName);

                // 检查目标是否已存在
                if (Files.exists(newPath)) {
                    showError("同名文件/文件夹已存在");
                    return;
                }

                // 执行重命名
                Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);

                System.out.println("重命名: " + oldPath + " -> " + newPath);
                statusLabel.setText("已重命名为: " + newName);

                // 刷新目录显示
                refreshDirectory();

                // 在文件表格中选中重命名的项目
                selectNewItemInTable(newName);

            } else {
                System.out.println("用户取消了重命名操作");
            }

        } catch (AccessDeniedException e) {
            System.err.println("无权限重命名: " + e.getMessage());
            showError("无权限重命名此项目");
        } catch (IOException e) {
            System.err.println("重命名失败: " + e.getMessage());
            showError("重命名失败: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("重命名异常: " + e.getMessage());
            e.printStackTrace();
            showError("重命名时发生错误");
        }
    }


    /**
 * 设置双击事件 - 修复冲突
 */
/**
 * 设置双击事件 - 修复冲突
 */
/**
 * 设置双击事件 - 修复冲突
 */
    /**
     * 设置双击事件 - 修复冲突
     */
    private void setupDoubleClickEvent() {
        // 移除 setupMultipleSelection 中的双击处理，统一在此处处理
        fileTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !event.isConsumed()) { // 双击事件
                FileItem selectedItem = fileTable.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    handleDoubleClick(selectedItem);
                }
                event.consume(); // 标记事件已处理，防止其他事件处理器重复处理
            }
        });
    }




    /**
     * 处理双击事件
     */
    /**
 * 处理双击事件
 */
private void handleDoubleClick(FileItem item) {
    if (item == null) {
        return;
    }

    try {
        Path itemPath = item.getPath();
        if (item.isDirectory()) {
            // 如果是文件夹，导航到该文件夹
            String folderPath = itemPath.toAbsolutePath().normalize().toString();
            currentPath = Paths.get(folderPath);
            navigateToFolder(folderPath);
        } else {
            // 如果是文件，尝试打开文件
            openFile(itemPath);
        }
    } catch (Exception e) {
        System.err.println("双击处理异常: " + e.getMessage());
        e.printStackTrace();
        Platform.runLater(() -> {
            showError("双击处理失败: " + e.getMessage());
        });
    }
}

/**
 * 打开文件
 */
void openFile(Path filePath) {
    try {
        Path normalizedPath = filePath.normalize();

        // 1. 检查文件是否存在
        if (!Files.exists(normalizedPath)) {
            Platform.runLater(() -> {
                showError("文件不存在: " + normalizedPath);
            });
            return;
        }

        // 2. 检查是否为文件夹
        if (Files.isDirectory(normalizedPath)) {
            Platform.runLater(() -> {
                showError("请使用双击打开文件夹: " + normalizedPath);
            });
            return;
        }

        // 3. 检查文件类型（修改后的，支持更多类型）
        String fileName = normalizedPath.toString().toLowerCase();
        if (!isValidFileType(fileName)) {
            Platform.runLater(() -> {
                showError("不支持的文件类型: " + normalizedPath);
            });
            return;
        }

        // 4. 检查操作系统类型
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();

            if (desktop.isSupported(Desktop.Action.OPEN)) {
                // 在后台线程中打开文件
                new Thread(() -> {
                    try {
                        desktop.open(normalizedPath.toFile());
                        System.out.println("成功打开文件: " + normalizedPath);
                    } catch (IOException e) {
                        Platform.runLater(() -> {
                            System.err.println("打开文件失败: " + e.getMessage());
                            showError("无法打开文件: " + e.getMessage());
                        });
                    }
                }).start();
            } else {
                Platform.runLater(() -> {
                    showError("当前系统不支持打开文件操作");
                });
            }
        } else {
            Platform.runLater(() -> {
                showError("当前系统不支持桌面操作");
            });
        }
    } catch (Exception e) {
        System.err.println("打开文件时发生错误: " + e.getMessage());
        Platform.runLater(() -> {
            showError("打开文件时发生错误: " + e.getMessage());
        });
    }
}

    /**
     * 打开文件
     */
//   private void openFile(Path filePath) {
//    try {
//        // 路径安全验证，防止路径遍历攻击
//        Path normalizedPath = filePath.normalize();
//        if (!normalizedPath.toAbsolutePath().startsWith(System.getProperty("user.home"))) {
//            showError("不允许访问的文件路径: " + filePath);
//            return;
//        }
//
//        if (!Files.exists(normalizedPath)) {
//            showError("文件不存在: " + normalizedPath);
//            return;
//        }
//
//        // 检查文件类型安全性
//        String fileName = normalizedPath.toString().toLowerCase();
//        if (!isValidFileType(fileName)) {
//            showError("不支持的文件类型: " + normalizedPath);
//            return;
//        }
//
//        // 检查操作系统类型
//        String osName = System.getProperty("os.name").toLowerCase();
//
//        if (Desktop.isDesktopSupported()) {
//            Desktop desktop = Desktop.getDesktop();
//
//            if (desktop.isSupported(Desktop.Action.OPEN)) {
//                // 在后台线程中打开文件，避免阻塞UI
//                new Thread(() -> {
//                    try {
//                        desktop.open(normalizedPath.toFile());
//                        System.out.println("成功打开文件: " + normalizedPath);
//                    } catch (IOException e) {
//                        Platform.runLater(() -> {
//                            System.err.println("打开文件失败: " + e.getMessage());
//                            showError("无法打开文件: " + e.getMessage());
//                        });
//                    }
//                }).start();
//            } else {
//                showError("当前系统不支持打开文件操作");
//            }
//        } else {
//            showError("当前系统不支持桌面操作");
//        }
//    } catch (Exception e) {
//        System.err.println("打开文件时发生错误: " + e.getMessage());
//        showError("打开文件时发生错误: " + e.getMessage());
//    }
//}

/**
 * 验证文件类型是否安全
 * @param fileName 文件名
 * @return 是否为安全的文件类型
 */
private boolean isValidFileType(String fileName) {
    // 更全面的文件扩展名列表
    String[] allowedExtensions = {
            // 文本和文档
            ".txt", ".md", ".rtf", ".pdf",
            // Office文档
            ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            // 图片
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".svg", ".ico",
            // 音频视频
            ".mp3", ".mp4", ".avi", ".mov", ".wav", ".flac", ".mkv", ".wmv", ".webm",
            // 网页
            ".html", ".htm", ".xml", ".css", ".js", ".json",
            // 代码文件
            ".java", ".py", ".cpp", ".c", ".h", ".cs", ".php", ".rb", ".go", ".rs",
            // 配置文件
            ".yml", ".yaml", ".ini", ".cfg", ".config", ".properties",
            // 其他常见格式
            ".zip", ".rar", ".7z", ".tar", ".gz", ".csv", ".log"
    };

    // 特殊处理：总是允许打开某些类型的文件
    String fileNameLower = fileName.toLowerCase();

    for (String ext : allowedExtensions) {
        if (fileNameLower.endsWith(ext)) {
            return true;
        }
    }
    return false;
}


    /**
     * 用系统默认程序打开文件
     */
    private void openFileWithDefaultProgram(FileItem fileItem) {
        try {
            System.out.println("尝试打开文件: " + fileItem.getPath());

            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                    desktop.open(fileItem.getPath().toFile());
                    statusLabel.setText("已打开文件: " + fileItem.getName());
                } else {
                    showError("当前系统不支持打开文件操作");
                }
            } else {
                showError("当前系统不支持桌面操作");
            }
        } catch (IOException e) {
            System.err.println("打开文件失败: " + e.getMessage());
            showError("无法打开文件: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("打开文件时发生错误: " + e.getMessage());
            showError("打开文件时发生错误: " + e.getMessage());
        }
    }

    private void initializeDirectoryTree() {
        try {
            System.out.println("开始初始化目录树...");

            // 创建根节点
            TreeItem<String> rootItem = new TreeItem<>("我的电脑");
            rootItem.setExpanded(true);

            // 获取磁盘驱动器
            List<DiskUtils.DiskInfo> disks = DiskUtils.getDiskDrives();
            System.out.println("找到 " + disks.size() + " 个磁盘驱动器");

            for (DiskUtils.DiskInfo disk : disks) {
                System.out.println("添加磁盘: " + disk.getPath() + " - " + disk.getName());

                TreeItem<String> diskItem = new TreeItem<>(disk.getName() + " (" + disk.getPath() + ")");
                rootItem.getChildren().add(diskItem);

                // 为磁盘项添加临时子项（用于显示展开箭头）
                TreeItem<String> tempItem = new TreeItem<>("点击展开查看内容...");
                diskItem.getChildren().add(tempItem);

                // 监听展开事件
                diskItem.expandedProperty().addListener((observable, oldValue, newValue) -> {
                    if (newValue) {
                        // 延迟加载磁盘内容
                        new Thread(() -> {
                            try {
                                Thread.sleep(100); // 给UI一点时间响应
                                Platform.runLater(() -> {
                                    loadDiskContentsUltraSafe(diskItem, disk.getPath());
                                });
                            } catch (Exception e) {
                                System.err.println("延迟加载磁盘内容失败: " + e.getMessage());
                            }
                        }).start();
                    }
                });
            }

            // 设置目录树根节点
            directoryTree.setRoot(rootItem);

            // 添加目录树选择监听器
            directoryTree.getSelectionModel().selectedItemProperty().addListener(
                    (observable, oldValue, newValue) -> {
                        // 防止快速重复点击
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastClickTime < 500) { // 500ms内不再处理
                            return;
                        }
                        lastClickTime = currentTime;

                        // 如果正在更新目录树，跳过事件处理
                        if (updatingTree) {
                            return;
                        }

                        if (newValue != null) {
                            String path = extractPathFromTreeItem(newValue);
                            if (path != null) {
                                System.out.println("目录树点击: " + path);

                                // 检查是否是磁盘根目录
                                if (isDiskRoot(path)) {
                                    System.out.println("点击了磁盘根目录: " + path);
                                    // 对于磁盘根目录，使用安全导航
                                    safeNavigateToDisk(path);
                                    return;
                                }

                                // 更新当前路径
                                currentPath = Paths.get(path).toAbsolutePath().normalize();

                                // 导航
                                navigateToFolder(path);
                            }
                        }
                    }
            );

            System.out.println("目录树初始化完成");

        } catch (Exception e) {
            System.err.println("初始化目录树失败: " + e.getMessage());
            e.printStackTrace();
        }
    }



    /**
     * 安全地导航到磁盘根目录
     */
    private void safeNavigateToDisk(String diskPath) {
        try {
            System.out.println("安全导航到磁盘: " + diskPath);

            // 更新当前路径
            currentPath = Paths.get(diskPath).toAbsolutePath().normalize();

            // 尝试读取目录内容，但使用更安全的方式
            new Thread(() -> {
                try {
                    List<FileItem> files = FileOperationService.listFiles(currentPath);

                    Platform.runLater(() -> {
                        try {
                            // 排序
                            SortUtils.sortFiles(files, SortUtils.SortBy.NAME, SortUtils.SortOrder.ASCENDING);

                            // 更新UI
                            fileTable.getItems().setAll(files);
                            pathField.setText(diskPath);
                            statusLabel.setText("磁盘: " + diskPath + " | 项目数: " + files.size());

                            // 更新目录树中的当前位置
                            updateCurrentLocationInTree();

                            System.out.println("安全导航完成");
                        } catch (Exception e) {
                            System.err.println("更新UI失败: " + e.getMessage());
                            showError("加载磁盘失败: " + e.getMessage());
                        }
                    });
                } catch (AccessDeniedException e) {
                    Platform.runLater(() -> {
                        System.err.println("访问被拒绝: " + e.getMessage());
                        statusLabel.setText("无法访问磁盘: " + diskPath);
                        showError("没有权限访问磁盘: " + diskPath);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        System.err.println("读取磁盘失败: " + e.getMessage());
                        statusLabel.setText("读取磁盘失败");
                        showError("无法读取磁盘: " + e.getMessage());
                    });
                }
            }).start();

        } catch (Exception e) {
            System.err.println("安全导航异常: " + e.getMessage());
            showError("导航失败: " + e.getMessage());
        }
    }

    /**
     * 超安全加载磁盘内容 - 使用Java IO File API避免崩溃
     */
    private void loadDiskContentsUltraSafe(TreeItem<String> diskItem, String diskPath) {
        System.out.println("超安全加载磁盘内容: " + diskPath);

        try {
            // 检查当前是否已经有子节点
            if (!diskItem.getChildren().isEmpty()) {
                TreeItem<String> firstChild = diskItem.getChildren().get(0);
                if (firstChild.getValue().equals("点击展开查看内容...")) {
                    // 更新为加载状态
                    firstChild.setValue("正在扫描磁盘...");
                }
            }

            // 在后台线程中加载内容
            new Thread(() -> {
                try {
                    // 使用Java IO的File API，它通常更稳定
                    File diskRoot = new File(diskPath);

                    // 检查磁盘是否存在且有读取权限
                    if (!diskRoot.exists() || !diskRoot.canRead()) {
                        Platform.runLater(() -> {
                            diskItem.getChildren().clear();
                            TreeItem<String> errorItem = new TreeItem<>("无法访问磁盘");
                            diskItem.getChildren().add(errorItem);
                        });
                        return;
                    }

                    // 列出磁盘根目录的内容
                    File[] files = diskRoot.listFiles();

                    Platform.runLater(() -> {
                        try {
                            // 清除所有子项
                            diskItem.getChildren().clear();

                            if (files == null || files.length == 0) {
                                // 如果为空，显示提示
                                TreeItem<String> emptyItem = new TreeItem<>("空文件夹");
                                diskItem.getChildren().add(emptyItem);
                                System.out.println("磁盘 " + diskPath + " 为空");
                                return;
                            }

                            int folderCount = 0;
                            for (File file : files) {
                                // 只处理文件夹
                                if (file.isDirectory()) {
                                    try {
                                        // 检查文件夹是否可访问
                                        if (!file.canRead()) {
                                            continue; // 跳过无权限的文件夹
                                        }

                                        TreeItem<String> folderItem = new TreeItem<>(file.getName());
                                        diskItem.getChildren().add(folderItem);
                                        folderCount++;

                                        // 为每个文件夹也添加临时子项
                                        TreeItem<String> tempSubItem = new TreeItem<>("...");
                                        folderItem.getChildren().add(tempSubItem);

                                        // 监听文件夹展开事件
                                        folderItem.expandedProperty().addListener((obs, oldVal, newVal) -> {
                                            if (newVal) {
                                                loadFolderContentsUltraSafe(folderItem, file.getPath());
                                            }
                                        });
                                    } catch (Exception e) {
                                        // 跳过有问题的文件夹
                                        System.err.println("跳过文件夹: " + file.getName() + " - " + e.getMessage());
                                    }
                                }

                                // 限制最大显示数量，避免卡顿
                                if (folderCount >= 100) {
                                    TreeItem<String> limitItem = new TreeItem<>("... (已显示前100个文件夹)");
                                    diskItem.getChildren().add(limitItem);
                                    break;
                                }
                            }

                            if (folderCount == 0) {
                                TreeItem<String> noFolderItem = new TreeItem<>("无可用文件夹");
                                diskItem.getChildren().add(noFolderItem);
                            }

                            System.out.println("超安全加载完成，共 " + folderCount + " 个文件夹");
                        } catch (Exception e) {
                            System.err.println("更新磁盘树失败: " + e.getMessage());
                            diskItem.getChildren().clear();
                            TreeItem<String> errorItem = new TreeItem<>("加载失败");
                            diskItem.getChildren().add(errorItem);
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        System.err.println("加载磁盘内容异常: " + e.getMessage());
                        diskItem.getChildren().clear();
                        TreeItem<String> errorItem = new TreeItem<>("加载异常");
                        diskItem.getChildren().add(errorItem);
                    });
                }
            }).start();

        } catch (Exception e) {
            System.err.println("超安全加载异常: " + e.getMessage());
        }
    }

    /**
     * 超安全加载文件夹内容
     */
    private void loadFolderContentsUltraSafe(TreeItem<String> parentItem, String folderPath) {
        System.out.println("超安全加载文件夹: " + folderPath);

        try {
            // 更新第一个子项为加载状态
            if (!parentItem.getChildren().isEmpty()) {
                TreeItem<String> firstChild = parentItem.getChildren().get(0);
                if (firstChild.getValue().equals("...")) {
                    firstChild.setValue("正在加载...");
                }
            }

            // 在后台线程中加载
            new Thread(() -> {
                try {
                    File folder = new File(folderPath);

                    // 检查文件夹是否存在且有读取权限
                    if (!folder.exists() || !folder.canRead()) {
                        Platform.runLater(() -> {
                            parentItem.getChildren().clear();
                            TreeItem<String> errorItem = new TreeItem<>("无法访问");
                            parentItem.getChildren().add(errorItem);
                        });
                        return;
                    }

                    File[] files = folder.listFiles();

                    Platform.runLater(() -> {
                        try {
                            // 清除所有子项
                            parentItem.getChildren().clear();

                            if (files == null || files.length == 0) {
                                TreeItem<String> emptyItem = new TreeItem<>("空文件夹");
                                parentItem.getChildren().add(emptyItem);
                                return;
                            }

                            int folderCount = 0;
                            for (File file : files) {
                                // 只处理文件夹
                                if (file.isDirectory()) {
                                    try {
                                        if (!file.canRead()) {
                                            continue; // 跳过无权限的文件夹
                                        }

                                        TreeItem<String> subFolderItem = new TreeItem<>(file.getName());
                                        parentItem.getChildren().add(subFolderItem);
                                        folderCount++;

                                        // 如果还有子文件夹，添加临时项
                                        if (hasSubFolders(file)) {
                                            TreeItem<String> tempItem = new TreeItem<>("...");
                                            subFolderItem.getChildren().add(tempItem);

                                            // 继续监听展开
                                            subFolderItem.expandedProperty().addListener((obs, oldVal, newVal) -> {
                                                if (newVal) {
                                                    loadFolderContentsUltraSafe(subFolderItem, file.getPath());
                                                }
                                            });
                                        }
                                    } catch (Exception e) {
                                        // 跳过有问题的文件夹
                                        System.err.println("跳过子文件夹: " + file.getName());
                                    }
                                }

                                // 限制最大显示数量
                                if (folderCount >= 50) {
                                    TreeItem<String> limitItem = new TreeItem<>("... (已显示前50个)");
                                    parentItem.getChildren().add(limitItem);
                                    break;
                                }
                            }

                            if (folderCount == 0) {
                                TreeItem<String> noFolderItem = new TreeItem<>("无子文件夹");
                                parentItem.getChildren().add(noFolderItem);
                            }
                        } catch (Exception e) {
                            System.err.println("更新文件夹树失败: " + e.getMessage());
                            parentItem.getChildren().clear();
                            TreeItem<String> errorItem = new TreeItem<>("加载失败");
                            parentItem.getChildren().add(errorItem);
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        System.err.println("加载文件夹内容异常: " + e.getMessage());
                        parentItem.getChildren().clear();
                        TreeItem<String> errorItem = new TreeItem<>("加载异常");
                        parentItem.getChildren().add(errorItem);
                    });
                }
            }).start();

        } catch (Exception e) {
            System.err.println("超安全加载文件夹异常: " + e.getMessage());
        }
    }

    /**
     * 检查文件夹是否有子文件夹
     */
    private boolean hasSubFolders(File folder) {
        try {
            if (!folder.exists() || !folder.canRead()) {
                return false;
            }

            File[] files = folder.listFiles();
            if (files == null) {
                return false;
            }

            for (File file : files) {
                if (file.isDirectory()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 更新目录树中的当前位置
     */
    private void updateCurrentLocationInTree() {
        try {
            updatingTree = true;  // 标记正在更新目录树

            TreeItem<String> root = directoryTree.getRoot();
            if (root == null) {
                updatingTree = false;
                return;
            }

            // 移除旧的当前位置节点
            if (currentLocationNode != null) {
                root.getChildren().remove(currentLocationNode);
                currentLocationNode = null;
            }

            // 创建新的当前位置节点
            String displayPath = currentPath.toString();
            // 如果路径太长，截断显示
            if (displayPath.length() > 50) {
                displayPath = "..." + displayPath.substring(displayPath.length() - 47);
            }
            currentLocationNode = new TreeItem<>("当前位置: " + displayPath);

            // 将当前位置节点添加到根节点
            root.getChildren().add(currentLocationNode);

            System.out.println("目录树已更新当前位置: " + displayPath);

        } catch (Exception e) {
            System.err.println("更新目录树当前位置失败: " + e.getMessage());
        } finally {
            updatingTree = false;  // 标记更新完成
        }
    }

    private String extractPathFromTreeItem(TreeItem<String> item) {
        if (item == null) return null;

        // 构建完整路径
        StringBuilder path = new StringBuilder();
        TreeItem<String> current = item;

        while (current != null && !current.getValue().equals("我的电脑")) {
            if (path.length() > 0) {
                path.insert(0, "\\");
            }

            // 提取路径部分（移除显示名称中的额外信息）
            String value = current.getValue();
            if (value.contains("(") && value.contains(")")) {
                // 磁盘项格式：磁盘名 (路径)
                path.insert(0, value.substring(value.indexOf("(") + 1, value.indexOf(")")));
                break;
            } else if (value.startsWith("当前位置: ")) {
                // 当前位置节点，不提取路径
                return null;
            } else {
                // 目录项，直接使用名称
                path.insert(0, value);
            }

            current = current.getParent();
        }

        return path.length() > 0 ? path.toString() : null;
    }

    @FXML
    public void refreshDirectory() {
        try {
            System.out.println("=== refreshDirectory() 被调用 ===");

            if (currentPath == null) {
                System.err.println("currentPath 为 null");
                return;
            }

            System.out.println("当前路径: " + currentPath.toString());
            String currentPathStr = currentPath.toString();

            // 获取文件列表
            List<FileItem> files = FileOperationService.listFiles(currentPath);
            SortUtils.sortFiles(files, SortUtils.SortBy.NAME, SortUtils.SortOrder.ASCENDING);

            // 在UI线程中更新界面
            Platform.runLater(() -> {
                try {
                    // 1. 检查 allFileItems
                    if (allFileItems == null) {
                        allFileItems = FXCollections.observableArrayList();
                    }

                    // 2. 更新文件列表
                    allFileItems.clear();
                    allFileItems.addAll(files);

                    // 3. 清空搜索框
                    if (searchField != null) {
                        searchField.clear();
                    }

                    // 4. 直接设置文件到表格
                    fileTable.getItems().setAll(files);

                    // 5. 更新路径显示
                    if (pathField != null) {
                        pathField.setText(currentPathStr);
                    }

                    // 6. 计算基本统计
                    int totalFiles = files.size();
                    int folderCount = 0;
                    int fileCount = 0;
                    long totalSize = 0;

                    for (FileItem file : files) {
                        if (file.isDirectory()) {
                            folderCount++;
                        } else {
                            fileCount++;
                            totalSize += file.getSize();
                        }
                    }

                    // 7. 更新状态栏
                    String sizeStr = formatFileSize(totalSize);
                    String statusText = "路径: " + currentPathStr +
                            " | 总计: " + totalFiles +
                            " (文件: " + fileCount +
                            ", 文件夹: " + folderCount +
                            ", 大小: " + sizeStr + ")";

                    if (statusLabel != null) {
                        statusLabel.setText(statusText);
                    }

                    System.out.println("状态栏文本: " + statusText);

                    // 8. 更新目录树中的当前位置
                    updateCurrentLocationInTree();

                } catch (Exception e) {
                    System.err.println("更新UI时发生错误: " + e.getMessage());
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            System.err.println("刷新目录失败: " + e.getMessage());
            e.printStackTrace();
            showError("加载目录失败: " + e.getMessage());
        }
    }

    @FXML
    public void exitApplication() {
        Platform.exit();
    }

    @FXML
    public void sortByName() {
        try {
            // 获取当前显示的列表
            List<FileItem> files = new ArrayList<>(fileTable.getItems());
            if (files.isEmpty()) {
                statusLabel.setText("没有可排序的项目");
                return;
            }

            System.out.println("开始按名称排序，共 " + files.size() + " 个项目");
            SortUtils.sortFiles(files, SortUtils.SortBy.NAME, SortUtils.SortOrder.ASCENDING);

            // 清空并重新设置表格数据
            fileTable.getItems().clear();
            fileTable.getItems().addAll(files);

            statusLabel.setText("已按名称排序 (" + files.size() + " 个项目)");
            System.out.println("排序完成");

        } catch (Exception e) {
            System.err.println("排序失败: " + e.getMessage());
            e.printStackTrace();
            showError("排序失败: " + e.getMessage());
        }
    }

    // 同样的方法应用到 sortBySize 和 sortByTime
    @FXML
    public void sortBySize() {
        try {
            List<FileItem> files = new ArrayList<>(fileTable.getItems());
            if (files.isEmpty()) {
                statusLabel.setText("没有可排序的项目");
                return;
            }

            System.out.println("开始按大小排序，共 " + files.size() + " 个项目");
            SortUtils.sortFiles(files, SortUtils.SortBy.SIZE, SortUtils.SortOrder.ASCENDING);

            // 清空并重新设置表格数据
            fileTable.getItems().clear();
            fileTable.getItems().addAll(files);

            statusLabel.setText("已按大小排序 (" + files.size() + " 个项目)");
            System.out.println("排序完成");

        } catch (Exception e) {
            System.err.println("排序失败: " + e.getMessage());
            e.printStackTrace();
            showError("排序失败: " + e.getMessage());
        }
    }

    @FXML
    public void sortByTime() {
        try {
            List<FileItem> files = new ArrayList<>(fileTable.getItems());
            if (files.isEmpty()) {
                statusLabel.setText("没有可排序的项目");
                return;
            }

            System.out.println("开始按修改时间排序，共 " + files.size() + " 个项目");
            SortUtils.sortFiles(files, SortUtils.SortBy.DATE_MODIFIED, SortUtils.SortOrder.ASCENDING);

            // 清空并重新设置表格数据
            fileTable.getItems().clear();
            fileTable.getItems().addAll(files);

            statusLabel.setText("已按修改时间排序 (" + files.size() + " 个项目)");
            System.out.println("排序完成");

        } catch (Exception e) {
            System.err.println("排序失败: " + e.getMessage());
            e.printStackTrace();
            showError("排序失败: " + e.getMessage());
        }
    }

//    @FXML
//    public void searchFiles() {
//        String pattern = searchField.getText();
//        if (pattern == null || pattern.trim().isEmpty()) {
//            refreshDirectory();
//            return;
//        }
//
//        pattern = pattern.trim();
//
//        try {
//            // 获取搜索类型
//            String searchType = "文件名"; // 默认
//            if (searchTypeCombo != null) {
//                searchType = searchTypeCombo.getSelectionModel().getSelectedItem();
//            }
//
//            // 是否区分大小写
//            boolean caseSensitive = false;
//            if (caseSensitiveCheckBox != null) {
//                caseSensitive = caseSensitiveCheckBox.isSelected();
//            }
//
//            // 获取当前路径
//            String currentPathStr = currentPath.toString();
//            System.out.println("在路径: " + currentPathStr + " 搜索模式: " + pattern + " 类型: " + searchType);
//
//            List<FileItem> results = new ArrayList<>();
//
//            // 根据搜索类型执行不同的搜索
//            switch (searchType) {
//                case "文件名":
//                    results = SearchUtils.searchByName(currentPath, pattern);
//                    break;
//
//                case "文件扩展名":
//                    // 如果用户输入 .txt，自动添加*通配符
//                    if (pattern.startsWith(".") && !pattern.contains("*")) {
//                        pattern = "*" + pattern;
//                    }
//                    results = SearchUtils.searchByName(currentPath, pattern);
//                    break;
//
//                case "大文件":
//                    // 搜索大于指定大小的文件（例如大于10MB）
//                    long minSize = 10 * 1024 * 1024; // 10MB
//                    try {
//                        if (pattern.matches("\\d+")) {
//                            long sizeMB = Long.parseLong(pattern);
//                            minSize = sizeMB * 1024 * 1024;
//                        }
//                    } catch (NumberFormatException e) {
//                        // 使用默认10MB
//                    }
//                    results = SearchUtils.searchLargeFiles(currentPath, minSize);
//                    break;
//
//                case "图片":
//                    results = SearchUtils.searchByType(currentPath, FileItem.FileType.IMAGE);
//                    break;
//
//                case "文档":
//                    results = SearchUtils.searchByType(currentPath, FileItem.FileType.DOCUMENT);
//                    break;
//
//                case "视频":
//                    results = SearchUtils.searchByType(currentPath, FileItem.FileType.VIDEO);
//                    break;
//
//                case "音频":
//                    results = SearchUtils.searchByType(currentPath, FileItem.FileType.AUDIO);
//                    break;
//
//                default:
//                    results = SearchUtils.searchByName(currentPath, pattern);
//                    break;
//            }
//
//            System.out.println("找到 " + results.size() + " 个结果");
//
//            if (results.isEmpty()) {
//                statusLabel.setText("没有找到匹配项");
//                String finalPattern = pattern;
//                Platform.runLater(() -> {
//                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
//                    alert.setTitle("搜索结果");
//                    alert.setHeaderText(null);
//                    alert.setContentText("没有找到匹配项: " + finalPattern);
//                    alert.show();
//                });
//            } else {
//                // 如果需要区分大小写，过滤结果
//                if (caseSensitive && !searchType.equals("大文件") && !searchType.endsWith("件类型")) {
//                    List<FileItem> filteredResults = new ArrayList<>();
//                    for (FileItem item : results) {
//                        if (item.getName().contains(pattern)) {
//                            filteredResults.add(item);
//                        }
//                    }
//                    results = filteredResults;
//                }
//
//                fileTable.getItems().setAll(results);
//                statusLabel.setText("找到 " + results.size() + " 个匹配项");
//
//                // 可选：排序结果
//                fileTable.getSortOrder().clear();
//                fileTable.getSortOrder().add(nameColumn);
//                nameColumn.setSortType(TableColumn.SortType.ASCENDING);
//                fileTable.sort();
//            }
//
//        } catch (Exception e) {
//            System.err.println("搜索错误: " + e.getMessage());
//            e.printStackTrace();
//            Platform.runLater(() -> {
//                showError("搜索失败: " + e.getMessage());
//            });
//        }
//    }
@FXML
public void searchFiles() {
    String pattern = searchField.getText();
    if (pattern == null || pattern.trim().isEmpty()) {
        refreshDirectory();
        return;
    }

    pattern = pattern.trim();

    // 添加到搜索历史
    if (!pattern.isEmpty() && !searchHistory.contains(pattern)) {
        addToSearchHistory(pattern);
    }

    // 添加调试日志
    System.out.println("=== 开始搜索 ===");
    System.out.println("搜索模式: " + pattern);
    System.out.println("当前路径: " + currentPath);

    try {
        // 检查当前路径
        if (currentPath == null || !Files.exists(currentPath) || !Files.isDirectory(currentPath)) {
            showError("当前路径无效: " + currentPath);
            return;
        }

        // 记录开始时间
        long startTime = System.currentTimeMillis();

        // 使用SearchUtils搜索
        List<FileItem> results = SearchUtils.searchByName(currentPath, pattern);

        // 记录结束时间
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("搜索完成，耗时: " + duration + "ms");
        System.out.println("找到 " + results.size() + " 个结果");

        if (results.isEmpty()) {
            statusLabel.setText("没有找到匹配 '" + pattern + "' 的文件");
            String finalPattern = pattern;
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("搜索结果");
                alert.setHeaderText(null);
                alert.setContentText("在 " + currentPath + " 中\n没有找到匹配 '" + finalPattern + "' 的文件");
                alert.show();
            });
        } else {
            // 显示结果
            fileTable.getItems().setAll(results);

            // 排序结果
            fileTable.getSortOrder().clear();
            fileTable.getSortOrder().add(nameColumn);
            nameColumn.setSortType(TableColumn.SortType.ASCENDING);
            fileTable.sort();

            // 更新状态栏
            String sizeText = formatTotalSize(results);
            statusLabel.setText("找到 " + results.size() + " 个匹配 '" + pattern + "' 的文件 | " + sizeText);

            // 可选：显示搜索结果摘要
            showSearchSummary(results, pattern, duration);
        }

    } catch (Exception e) {
        System.err.println("搜索错误: " + e.getMessage());
        e.printStackTrace();
        Platform.runLater(() -> {
            showError("搜索失败: " + e.getMessage() + "\n请确保您有访问权限。");
        });
    }
}

    /**
     * 计算搜索结果总大小
     */
    private String formatTotalSize(List<FileItem> results) {
        long totalSize = 0;
        int fileCount = 0;
        int dirCount = 0;

        for (FileItem item : results) {
            if (item.isDirectory()) {
                dirCount++;
            } else {
                fileCount++;
                totalSize += item.getSize();
            }
        }

        if (totalSize < 1024) {
            return String.format("文件: %d, 文件夹: %d, 大小: %d B", fileCount, dirCount, totalSize);
        } else if (totalSize < 1024 * 1024) {
            return String.format("文件: %d, 文件夹: %d, 大小: %.1f KB", fileCount, dirCount, totalSize / 1024.0);
        } else if (totalSize < 1024 * 1024 * 1024) {
            return String.format("文件: %d, 文件夹: %d, 大小: %.1f MB", fileCount, dirCount, totalSize / (1024.0 * 1024.0));
        } else {
            return String.format("文件: %d, 文件夹: %d, 大小: %.1f GB", fileCount, dirCount, totalSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 显示搜索摘要
     */
    private void showSearchSummary(List<FileItem> results, String pattern, long duration) {
        // 可以添加一个信息弹窗显示详细结果
        StringBuilder summary = new StringBuilder();
        summary.append("搜索完成！\n\n");
        summary.append("搜索模式: ").append(pattern).append("\n");
        summary.append("搜索路径: ").append(currentPath).append("\n");
        summary.append("搜索耗时: ").append(duration).append("ms\n");
        summary.append("找到结果: ").append(results.size()).append(" 个\n");

        // 统计文件类型
        Map<FileItem.FileType, Integer> typeCount = new HashMap<>();
        for (FileItem item : results) {
            typeCount.merge(item.getType(), 1, Integer::sum);
        }

        if (!typeCount.isEmpty()) {
            summary.append("\n按类型统计:\n");
            for (Map.Entry<FileItem.FileType, Integer> entry : typeCount.entrySet()) {
                summary.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        // 记录日志
        System.out.println(summary.toString());
    }
    /**
     * 设置实时搜索
     */
    /**
     * 设置实时搜索功能
     */
    private void setupRealtimeSearch() {
        if (searchField != null) {
            // 监听搜索框文本变化
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (enableRealtimeSearch && newValue != null && newValue.length() >= 1) {
                    // 防抖处理，延迟500ms后执行搜索
                    if (searchTimer != null) {
                        searchTimer.cancel();
                    }

                    searchTimer = new Timer("SearchTimer", true);
                    TimerTask searchTask = new TimerTask() {
                        @Override
                        public void run() {
                            Platform.runLater(() -> {
                                // 只有当搜索框不为空时才执行搜索
                                if (searchField.getText() != null && !searchField.getText().trim().isEmpty()) {
                                    System.out.println("实时搜索: " + searchField.getText());
                                    searchFiles();
                                }
                            });
                        }
                    };
                    searchTimer.schedule(searchTask, REALTIME_SEARCH_DELAY);
                }
            });


        }
    }



    private void navigateToFolder(String folderPath) {
        try {
            System.out.println("=== 开始导航到文件夹 ===");
            System.out.println("原始路径: " + folderPath);

            // 转换为绝对路径并规范化
            Path path = Paths.get(folderPath).toAbsolutePath().normalize();
            String absolutePath = path.toString();
            System.out.println("规范化路径: " + absolutePath);

            // 更新当前路径
            currentPath = path;

            System.out.println("路径是否存在: " + Files.exists(path));
            System.out.println("是否是文件夹: " + Files.isDirectory(path));

            if (!Files.exists(path)) {
                showError("路径不存在: " + absolutePath);
                return;
            }

            if (!Files.isDirectory(path)) {
                showError("这不是一个文件夹: " + absolutePath);
                return;
            }

            // 尝试读取目录内容
            System.out.println("正在读取文件夹内容...");
            List<FileItem> files = FileOperationService.listFiles(path);
            System.out.println("获取到 " + files.size() + " 个项目");

            // 排序
            SortUtils.sortFiles(files, SortUtils.SortBy.NAME, SortUtils.SortOrder.ASCENDING);

            // 在UI线程中更新界面
            // 在 Platform.runLater 中更新表格
            Platform.runLater(() -> {
                try {
                    // 1. 检查 allFileItems
                    if (allFileItems == null) {
                        allFileItems = FXCollections.observableArrayList();
                    }

                    // 2. 清空搜索框
                    if (searchField != null) {
                        searchField.clear();
                    }

                    // 3. 清空表格当前数据
                    fileTable.getItems().clear();
                    System.out.println("清空表格，准备添加 " + files.size() + " 个项目");

                    // 4. 添加新数据
                    for (FileItem file : files) {
                        fileTable.getItems().add(file);
                    }
                    System.out.println("表格数据已更新");

                    // 5. 更新 allFileItems
                    allFileItems.clear();
                    allFileItems.addAll(files);

                    // 6. 更新路径显示
                    if (pathField != null) {
                        pathField.setText(absolutePath);
                    }

                    // 7. 计算基本统计
                    int totalFiles = files.size();
                    int folderCount = 0;
                    int fileCount = 0;
                    long totalSize = 0;

                    for (FileItem file : files) {
                        if (file.isDirectory()) {
                            folderCount++;
                        } else {
                            fileCount++;
                            totalSize += file.getSize();
                        }
                    }

                    // 8. 更新状态栏
                    String sizeStr = formatFileSize(totalSize);
                    String statusText = "路径: " + absolutePath +
                            " | 总计: " + totalFiles +
                            " (文件: " + fileCount +
                            ", 文件夹: " + folderCount +
                            ", 大小: " + sizeStr + ")";

                    if (statusLabel != null) {
                        statusLabel.setText(statusText);
                    }

                    System.out.println("状态栏文本: " + statusText);

                    // 9. 更新目录树中的当前位置
                    updateCurrentLocationInTree();

                    System.out.println("=== 导航完成 ===");

                } catch (Exception e) {
                    System.err.println("更新UI时发生错误: " + e.getMessage());
                    e.printStackTrace();
                    showError("更新界面失败: " + e.getMessage());
                }
            });

        } catch (AccessDeniedException e) {
            System.err.println("访问被拒绝: " + e.getMessage());
            e.printStackTrace();
            showError("没有权限访问文件夹: " + folderPath);

        } catch (IOException e) {
            System.err.println("IO异常: " + e.getMessage());
            e.printStackTrace();
            showError("无法读取文件夹: " + e.getMessage());

        } catch (Exception e) {
            System.err.println("未知异常: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            showError("访问文件夹失败: " + e.getMessage());
        }
    }

    @FXML
    public void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("关于");
        alert.setHeaderText("文件管理器 v1.0");
        alert.setContentText("基于Java 21和JavaFX开发\n功能：文件浏览、搜索、排序、磁盘信息查看");
        alert.showAndWait();
    }

    private void showError(String message) {
        statusLabel.setText("错误: " + message);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setContentText(message);
        alert.show();
    }


}
