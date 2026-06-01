package com.fileexplorer.controller;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SimpleProgressDialog {
    private Stage dialogStage;
    private ProgressBar progressBar;
    private Label messageLabel;

    public SimpleProgressDialog() {
        dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.UTILITY);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("请稍候");
        dialogStage.setResizable(false);

        progressBar = new ProgressBar();
        progressBar.setPrefWidth(300);

        messageLabel = new Label("正在处理...");

        VBox vbox = new VBox(20);
        vbox.setPadding(new Insets(20));
        vbox.getChildren().addAll(messageLabel, progressBar);

        Scene scene = new Scene(vbox);
        dialogStage.setScene(scene);
    }

    public void show(String message) {
        messageLabel.setText(message);
        progressBar.setProgress(-1); // 不确定进度
        dialogStage.show();
    }

    public void showWithProgress(String message, double progress) {
        messageLabel.setText(message);
        progressBar.setProgress(progress);
        dialogStage.show();
    }

    public void close() {
        dialogStage.close();
    }

    public void updateProgress(double progress) {
        progressBar.setProgress(progress);
    }

    public void updateMessage(String message) {
        messageLabel.setText(message);
    }
}
