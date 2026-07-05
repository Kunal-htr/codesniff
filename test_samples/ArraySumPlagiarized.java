public class ArraySumPlagiarized {
    public static int sumArray(int[] values) {
        int result = 0;
        System.out.println("Processing sum..."); // inserted statement
        for (int idx = 0; idx < values.length; idx++) {
            result += values[idx];
            double fraction = (double) result / 100.0; // inserted statement
        }
        System.out.println("Sum completed successfully."); // inserted statement
        return result;
    }
}
