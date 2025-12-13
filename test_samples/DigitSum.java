public class DigitSum {
    public static void main(String[] args) {

        int n = 4579;  // input number
        int s = 0;
        int x = n;

        while (x != 0) {
            s = s + (x % 10);
            x = x / 10;
        }

        System.out.println("Total sum of digits is: " + s);
    }
}
