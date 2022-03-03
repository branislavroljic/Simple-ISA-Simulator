package application;

import java.io.File;
import java.util.Optional;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TextInputDialog;

public class Main extends Application {
	@Override
	public void start(Stage primaryStage) {
		try {
			
			//unos putanje do fajla
			TextInputDialog dialog = new TextInputDialog("Ulaz");
			dialog.setTitle("");
			dialog.setHeaderText("Enter file path!");
			dialog.setContentText("File path:");

			Optional<String> result = dialog.showAndWait();
			if (result.isPresent() && new File(result.get()).exists()) {
				Interpreter interpreter = new Interpreter(result.get());
				interpreter.showConsoleStage();
			}else {
				InterpreterUtilities.showAlert(AlertType.ERROR, "ERROR", "File doesn't exist", "");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}
