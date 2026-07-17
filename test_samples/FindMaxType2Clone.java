public class FindMaxType2Clone {
    public static int locateLargest(int[] numbers) {
        if (numbers == null || numbers.length == 0) {
            return -1;
        }
        int largest = numbers[0];
        for (int idx = 1; idx < numbers.length; idx++) {
            if (numbers[idx] > largest) {
                largest = numbers[idx];
            }
        }
        return largest;
    }
}
