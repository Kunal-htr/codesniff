public class ArraySumType1Clone {
    // Computes the sum of all elements in an array
    public static int sumArray(int[] arr) {
        int total = 0;
        for (int i = 0; i < arr.length; i++) {
            total += arr[i];
        }
        return total;
    }
}
