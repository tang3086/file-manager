module com.fileexplorer {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires org.testng;
    requires javafx.graphics;

    opens com.fileexplorer to javafx.fxml;
    opens com.fileexplorer.controller to javafx.fxml;
    opens com.fileexplorer.model to javafx.base;

    exports com.fileexplorer;
    exports com.fileexplorer.controller;
    exports com.fileexplorer.model;
}