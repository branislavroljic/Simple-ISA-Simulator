package application;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

//utilities
public class InterpreterUtilities {

	// pronalazak odgovarajuce labele, upotreba kod instrukcija za uslovno grananje
	static void findLabel(String label) {
		Interpreter.lineCounter = 0;
		while (!Interpreter.lines.get(Interpreter.lineCounter).trim().startsWith(label + ":")) {
			Interpreter.lineCounter++;
		}
	}

	public static boolean isConditionalJumpInstruction(String instruction) {
		return false
		|| instruction.equalsIgnoreCase(Instruction.JE.name())
		|| instruction.equalsIgnoreCase(Instruction.JZ.name())
		|| instruction.equalsIgnoreCase(Instruction.JA.name())
		|| instruction.equalsIgnoreCase(Instruction.JB.name())
		|| instruction.equalsIgnoreCase(Instruction.JNE.name());
	}
	//dohvatanje vrijednosti iz memorijske adrese ili registra
	// omoguceno je registarsko adresiranje, neposredno adresiranje, direktno
	// adresiranje, registarsko indirektno adresiranje, relativno registarsko
	// adresiranje, bazno indeksno adresiranje, relativno bazno indeksno
	// adresiranje, skalarno ineksno adresiranje
	// bazni i indeksni registri mogu biti bilo koji od 4 navedena, omoguceno je  [R1 + R2*3 + 5*R3 + 17] tj. proizvoljan broj "sabiraka"
	static Long getValueFromOperand(String operand) {
		//operand je registar
		if (InterpreterUtilities.isRegister(operand)) {
			return InterpreterUtilities.getRegister(operand).getValue();
			//operand je memorijska lokacija
		} else if (InterpreterUtilities.isMemoryAddress(operand)) {
			String address = operand.substring(1, operand.length() - 1);
			//registarsko indirektno adresiranje
			if (InterpreterUtilities.isRegister(address)) {
				return Interpreter.memoryMap.get(InterpreterUtilities.getRegister(address).getValue());
			} else {
				//ostali oblici adresiranja
				if (address.contains("+")) {
					String[] operands = address.split("\\+");
					Long addressValue = Long.valueOf(0);
					for (String op : operands) {
						op = op.trim();
						//operand operacije sabiranja je registar opste namjene
						if (InterpreterUtilities.isRegister(op)) {
							addressValue += InterpreterUtilities.getRegister(op).getValue();
							//operacija mnozenja
						} else if (op.contains("*")) {
							if (InterpreterUtilities.isRegister(op.split("\\*")[0])) {
								addressValue += InterpreterUtilities.getRegister(op.split("\\*")[0]).getValue()
										* Long.parseLong(op.split("\\*")[1]);
							} else if (InterpreterUtilities.isRegister(op.split("\\*")[1])) {
								addressValue += InterpreterUtilities.getRegister(op.split("\\*")[1]).getValue()
										* Long.parseLong(op.split("\\*")[0]);
							} else {
								//jedan operand kod mnozenja mora biti registar
								throw new IllegalArgumentException();
							}
							
						} else {
							addressValue += Long.parseLong(op);
						}
					}
					return Interpreter.memoryMap.get(addressValue);
				}
				return Interpreter.memoryMap.get(Long.parseLong(address));
			}
		} else {//operand je konstanta (neposredno adresiranje)
			if (operand.endsWith("h")) {
				return Long.parseLong(operand.substring(0, operand.length() - 1), 16);
			} else if (operand.endsWith("b")) {
				return Long.parseLong(operand.substring(0, operand.length() - 1), 2);
			} else
				return Long.parseLong(operand);
		}
	}

	//prikaz Alert-a
	public static void showAlert(AlertType type, String title, String headerText, String contextText) {
		Alert alert = new Alert(type);
		alert.setTitle(title);
		alert.setHeaderText(headerText);
		alert.setContentText(contextText);
		alert.showAndWait();
	}

	//podesavanje flegova u zavisnosti od znaka i vrijednosti proslijedjenog rezultata
	static void setFlags(Long res) {
		if (res < 0) {
			Interpreter.SF = 1;
			Interpreter.ZF = 0;
		} else if (res == 0) {
			Interpreter.ZF = 1;
			Interpreter.SF = 0;
		} else {
			Interpreter.SF = 0;
			Interpreter.ZF = 0;
		}
	}

	// dohvatanje Register ili Long objekta
	static Object getObjectFromOperand(String operand) {

		if (InterpreterUtilities.isRegister(operand)) {
			return InterpreterUtilities.getRegister(operand);
		} else if (InterpreterUtilities.isMemoryAddress(operand)) {
			String address = operand.substring(1, operand.length() - 1);
			if (InterpreterUtilities.isRegister(address)) {
				return InterpreterUtilities.getRegister(address).getValue();
			} else {
				return Long.parseLong(address);
			}
		} else {
			throw new IllegalArgumentException("Destination cannot be constant");
		}
	}

	//provjera da li je operand proslijedjen kao parametar registar
	static boolean isRegister(String operand) {
		for (Register reg : Interpreter.registers) {
			if (reg.getName().equalsIgnoreCase(operand))
				return true;
		}
		return false;
	}

	//dohvatanje registra cije ime je isto kao i proslijedjeni parametar
	static Register getRegister(String register) {
		for (Register reg : Interpreter.registers) {
			if (reg.getName().equalsIgnoreCase(register))
				return reg;
		}
		return null;
	}

	//provjera da li je operand memorijska adresa
	static boolean isMemoryAddress(String operand) {
		return operand.startsWith("[") && operand.endsWith("]");
	}

}
