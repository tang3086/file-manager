package com.fileexplorer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.fileexplorer.service.FileOperationService;

import java.io.IOException;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {
        // 设置未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("=== 未捕获的异常 ===");
            System.err.println("线程: " + thread.getName());
            System.err.println("异常类型: " + throwable.getClass().getName());
            System.err.println("异常信息: " + throwable.getMessage());
            throwable.printStackTrace();
            System.err.println("=================");
        });

        try {
            // 测试FXML文件是否存在
            java.net.URL fxmlUrl = getClass().getResource("/com/fileexplorer/view/main-view.fxml");
            if (fxmlUrl == null) {
                System.err.println("❌ FXML文件未找到！");
                showErrorScreen(primaryStage, "FXML文件未找到：/com/fileexplorer/view/main-view.fxml");
                return;
            }

            System.out.println("✅ FXML文件找到：" + fxmlUrl);

            // 尝试加载FXML
            FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);
            Scene scene = new Scene(fxmlLoader.load(), 1000, 700);

            primaryStage.setTitle("文件管理器");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            primaryStage.show();

        } catch (Exception e) {
            System.err.println("❌ 加载FXML失败：" + e.getMessage());
            e.printStackTrace();
            showErrorScreen(primaryStage, "加载失败：" + e.getMessage());
        }
    }

    private void showErrorScreen(Stage stage, String message) {
        javafx.scene.control.Label label = new javafx.scene.control.Label("错误信息：\n" + message);
        javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(label);
        Scene scene = new Scene(root, 600, 400);
        stage.setScene(scene);
        stage.setTitle("错误");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}