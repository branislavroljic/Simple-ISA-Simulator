package application;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.paint.Color;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

public class Interpreter implements Initializable {

	// mapa memorije, kljuc je ime registra ili memorijska lokacija, a vrijednost je
	// vrijednost unutar registra ili memorijske lokacije
	public static HashMap<Long, Long> memoryMap = new HashMap<Long, Long>();
	// registri opste namjene
	public static final Register R1 = new Register("R1");
	public static final Register R2 = new Register("R2");
	public static final Register R3 = new Register("R3");
	public static final Register R4 = new Register("R4");
	public static List<Register> registers = List.of(R1, R2, R3, R4);

	public static Scanner sc = new Scanner(System.in);
	public static List<String> lines;

	// brojac trenutne linije fajla koja se interpretira
	public static int lineCounter = 0;

	// cuvanje trenutnog stanja registara opste namjene
	private static List<Register> currentRegisterValues = new ArrayList<>();

	private File assemblyFile;
	private Stage consoleStage;
	private Stage debugStage;

	@FXML
	TextArea consoleTextArea;
	@FXML
	TextFlow debugTextArea;
	@FXML
	TableView<Register> debugTable;
	@FXML
	TableColumn<Register, String> registerName;
	@FXML
	TableColumn<Register, Long> registerValue;
	@FXML
	Button nextLineButton;

	// flagovi, ZF = 1 <=> rezultat prethodno izvrsene operacije jendak 0
	// SF = 1 <=> rezultat prethodne operacije je manji od 0
	static int ZF = 0;
	static int SF = 0;

	public Interpreter(String path) {

		assemblyFile = new File(path);
		this.consoleStage = new Stage();
		this.debugStage = new Stage();

		// Load the FXML file
		try {
			// GUI za console
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/application/InterpreterFX.fxml"));

			loader.setController(this);

			// Load the scene
			consoleStage.setScene(new Scene(loader.load()));
			consoleStage.setResizable(false);
			consoleStage.setOnCloseRequest(e -> {
				System.exit(0);
				Platform.exit();
			});

			// GUI za debugger
			FXMLLoader loader2 = new FXMLLoader(getClass().getResource("/application/Debugger.fxml"));
			loader2.setController(this);
			debugStage.setScene(new Scene(loader2.load()));
			debugStage.setResizable(false);
			debugStage.setOnCloseRequest(e -> {
				System.exit(0);
				Platform.exit();
			});

			currentRegisterValues.addAll(registers);

		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void initialize(URL arg0, ResourceBundle arg1) {
		nextLineButton.setVisible(false);
	}

	public void showConsoleStage() throws IOException {

		consoleStage.show();
		interpretAssemblyFile();
	}

	private void showDebugStage() {
		debugStage.showAndWait();
	}

	// interpretacija fajla
	private void interpretAssemblyFile() throws IOException {
		lines = Files.readAllLines(assemblyFile.toPath());

		String line = null;

		while (lineCounter < lines.size()) {
			line = lines.get(lineCounter).trim();

			// ako linija ne sadrzi neku od instrukcija
			if (line.isBlank() || line.trim().endsWith(":") && line.split(" ").length == 1) {
				lineCounter++;
				continue;
			}

			// ako linija pocinje sa *, korisnik zeli da se od te linije udje u debugging
			// mode
			// otvara se nova scena koja simulira debugger
			if (line.startsWith("*")) {
				line = line.substring(line.indexOf("*") + 1, line.length()).trim();
				System.out.println("Debugging mode.....");

				registerName.setCellValueFactory(new PropertyValueFactory<>("name"));

				registerValue.setCellValueFactory(new PropertyValueFactory<>("value"));

				nextLineButton.setVisible(true);
				updateDebuggerConsole();
				showDebugStage();
			}

			// obrada linije
			try {
				executeLine(line, false);
			} catch (Exception e) {
				e.printStackTrace();
				InterpreterUtilities.showAlert(AlertType.ERROR, "Error!", "Invalid instruction!", e.toString());
				nextLineButton.setVisible(false);
				return;
			}

			lineCounter++;
		}
	}

	// Debugging mode, korisnik stisnuo Button za prelazak na narednu liniju
	@FXML
	private void OnNextLineButton() {

//		if (lineCounter >= lines.size()) {
//			nextLineButton.setVisible(false);
//			return;
//		}

		// linije bez instrukcija se ignorisu
		String line;
		do {
			line = lines.get(lineCounter).trim();
			lineCounter++;
			if (lineCounter >= lines.size()) {
				nextLineButton.setVisible(false);
				return;
			}
		} while (line.isBlank() || line.trim().endsWith(":") && line.split(" ").length == 1);

		lineCounter--;

		updateDebuggerConsole();

		if (line.startsWith("*")) {
			line = line.substring(line.indexOf("*") + 1, line.length()).trim();
		}
		try {
			executeLine(line, true);
		} catch (Exception e) {
			e.printStackTrace();
			InterpreterUtilities.showAlert(AlertType.ERROR, "Error!", "Invalid instruction!", e.toString());
			nextLineButton.setVisible(false);
			return;
		}
		lineCounter++;

		debugTable.getItems().clear();
		debugTable.getItems().addAll(currentRegisterValues);

	}

	// obrada linije fajla
	private void executeLine(String line, boolean debuggingMode) {

		// patterni za prvi i drugi operand operacije
		Pattern firstOpPattern = Pattern.compile(" (.*).*?,");
		Pattern secondOpPattern = Pattern.compile(", *(.*)");
		Matcher firstOpMatcher, secondOpMatcher;

		String instruction = "", firstOp = "", secondOp = "";

		instruction = line.split(" ")[0].trim();

		firstOpMatcher = firstOpPattern.matcher(line);
		secondOpMatcher = secondOpPattern.matcher(line);
		if (!(firstOpMatcher.find() && secondOpMatcher.find())) {

			// ako operacija nije unarna, drugi oprerand mora postojati
			if (!(instruction.equalsIgnoreCase(Instruction.WRITE.name())
					|| instruction.equalsIgnoreCase(Instruction.READ.name())
					|| instruction.equalsIgnoreCase(Instruction.NOT.name())
					|| InterpreterUtilities.isConditionalJumpInstruction(instruction))) {
				throw new IllegalArgumentException(line);
			} else {
				firstOp = line.split(" ")[1].trim();
			}
		} else {
			firstOp = firstOpMatcher.group(1).trim();
			secondOp = secondOpMatcher.group(1).trim();
		}

		execInstruction(instruction, firstOp, secondOp);

		//instrukcije uslovnih skokova ne mijenjaju stanje registara
		if (InterpreterUtilities.isConditionalJumpInstruction(instruction))
			return;

		// i registar i memorijska lokacija se predstavljaju preko objekta Register radi lakseg prikaza u ListView
		Register op1 = new Register(firstOp.toUpperCase(), InterpreterUtilities.getValueFromOperand(firstOp));
		if (currentRegisterValues.contains(op1))
			currentRegisterValues.remove(op1);
		currentRegisterValues.add(op1);
	}

	// update dijela koji prikazuje sadrzaj fajla, kao i liniju
	// na kojoj je interpreter trenutno
	private void updateDebuggerConsole() {

		Text blackTextBeforeCounter, blackTextAfterCounter;
		String blackLinesBeforeCurrent = "";
		String blackLinesAfterCurrent = "";
		for (int i = 0; i < lineCounter; i++) {
			blackLinesBeforeCurrent += lines.get(i) + "\n";
		}

		for (int i = lineCounter + 1; i < lines.size(); i++) {
			blackLinesAfterCurrent += lines.get(i) + "\n";
		}
		Text currentLine = new Text(lines.get(lineCounter) + "\n");
		currentLine.setFill(Color.RED);
		blackTextBeforeCounter = new Text(blackLinesBeforeCurrent);
		blackTextAfterCounter = new Text(blackLinesAfterCurrent);
		blackTextBeforeCounter.setFill(Color.BLACK);
		blackTextAfterCounter.setFill(Color.BLACK);

		debugTextArea.getChildren().clear();
		debugTextArea.getChildren().addAll(blackTextBeforeCounter, currentLine, blackTextAfterCounter);

	}

	private void execInstruction(String instruction, String firstOp, String secondOp) {
		Instruction enumInstruction = Instruction.valueOf(instruction.toUpperCase());

		// oba operanda ne smiju biti memorijske lokacije
		if (InterpreterUtilities.isMemoryAddress(firstOp) && InterpreterUtilities.isMemoryAddress(secondOp)) {
			throw new IllegalArgumentException("One operand must be a register: line " + (lineCounter + 1));
		}
		Long sourceValue = null;
		Object destoperand = null;
		Long res;
		switch (enumInstruction) {

		case ADD:

			sourceValue = InterpreterUtilities.getValueFromOperand(secondOp);
			destoperand = InterpreterUtilities.getObjectFromOperand(firstOp);

			if (destoperand instanceof Register) {
				((Register) destoperand).setValue(res = ((Register) destoperand).getValue() + sourceValue);
			} else {
				memoryMap.put(((Long) destoperand), res = memoryMap.get(((Long) destoperand)) + sourceValue);
			}
			InterpreterUtilities.setFlags(res);
			break;
		case SUB:

			sourceValue = InterpreterUtilities.getValueFromOperand(secondOp);
			destoperand = InterpreterUtilities.getObjectFromOperand(firstOp);

			if (destoperand instanceof Register) {
				((Register) destoperand).setValue(res = ((Register) destoperand).getValue() - sourceValue);
			} else {
				memoryMap.put(((Long) destoperand), res = memoryMap.get(((Long) destoperand)) - sourceValue);
			}

			InterpreterUtilities.setFlags(res);
			break;

		case AND:
			sourceValue = InterpreterUtilities.getValueFromOperand(secondOp);
			destoperand = InterpreterUtilities.getObjectFromOperand(firstOp);

			if (destoperand instanceof Register) {
				((Register) destoperand).setValue(res = ((Register) destoperand).getValue() & sourceValue);
			} else {
				memoryMap.put(((Long) destoperand), res = memoryMap.get(((Long) destoperand)) & sourceValue);
			}
			InterpreterUtilities.setFlags(res);
			break;
		case OR:
			sourceValue = InterpreterUtilities.getValueFromOperand(secondOp);
			destoperand = InterpreterUtilities.getObjectFromOperand(firstOp);

			if (destoperand instanceof Register) {
				((Register) destoperand).setValue(res = ((Register) destoperand).getValue() | sourceValue);
			} else {
				memoryMap.put(((Long) destoperand), res = memoryMap.get(((Long) destoperand)) | sourceValue);
			}
			InterpreterUtilities.setFlags(res);
			break;
		case NOT:
			destoperand = InterpreterUtilities.getObjectFromOperand(firstOp);

			if (destoperand instanceof Register) {
				((Register) destoperand).setValue(res = ~((Register) destoperand).getValue());
			} else {
				memoryMap.put(((Long) destoperand), res = ~memoryMap.get(((Long) destoperand)));
			}
			InterpreterUtilities.setFlags(res);
			break;
		case MOV:
			sourceValue = InterpreterUtilities.getValueFromOperand(secondOp);
			destoperand = InterpreterUtilities.getObjectFromOperand(firstOp);

			if (destoperand instanceof Register) {
				((Register) destoperand).setValue(sourceValue);
			} else {
				memoryMap.put(((Long) destoperand), sourceValue);
			}
			break;
		case WRITE:
			consoleTextArea.appendText(firstOp + " = " + InterpreterUtilities.getValueFromOperand(firstOp) + "\n");
			break;
		case READ:
			Optional<String> result;
			do {
				TextInputDialog dialog = new TextInputDialog("");
				dialog.setTitle("");
				dialog.setHeaderText("Enter register/memory address value!");
				dialog.setContentText("Value:");

				result = dialog.showAndWait();
				if (!result.isPresent() || result.get().isEmpty())
					InterpreterUtilities.showAlert(AlertType.ERROR, "ERROR", "Value is not present", "");
			} while (!result.isPresent() || result.get().isEmpty());

			sourceValue = Long.parseLong(result.get());
			destoperand = InterpreterUtilities.getObjectFromOperand(firstOp);

			if (destoperand instanceof Register) {
				((Register) destoperand).setValue(sourceValue);
			} else {
				memoryMap.put(((Long) destoperand), sourceValue);
			}

			break;
		case CMP:
			Long firstOpValue = InterpreterUtilities.getValueFromOperand(firstOp);
			Long secondOpValue = InterpreterUtilities.getValueFromOperand(secondOp);
			if (firstOpValue > secondOpValue) {
				ZF = 0;
				SF = 0;
			} else if (secondOpValue > firstOpValue) {
				ZF = 0;
				SF = 1;
			} else {
				ZF = 1;
				SF = 0;
			}
			break;
		case JE:
			if (ZF == 1) {
				ZF = 0;
				InterpreterUtilities.findLabel(firstOp);
			}
			break;
		case JNE:
			if (ZF == 0) {
				InterpreterUtilities.findLabel(firstOp);
			}
			break;
		case JA:
			if (ZF == 0 && SF == 0) {
				InterpreterUtilities.findLabel(firstOp);
			}
			break;
		case JB:
			if (SF == 1) {
				InterpreterUtilities.findLabel(firstOp);
				SF = 0;
			}
			break;
		case JZ:
			if (ZF == 1) {
				InterpreterUtilities.findLabel(firstOp);
				ZF = 0;
			}
			break;
		default:
			throw new IllegalArgumentException("Unexpected value: " + instruction);
		}

	}
}

enum Instruction {
	ADD, SUB, AND, OR, NOT, MOV, READ, WRITE, CMP, JE, JNE, JA, JB, JZ;
}
