import javax.swing.JOptionPane;

public class ErrorDialog {
    public static void showError(String errorMessage) {
        JOptionPane.showMessageDialog(null, errorMessage, "错误", JOptionPane.ERROR_MESSAGE);
    }
}
