public class FindMin {
    public static int find(int[] arr) {
        if (arr == null || arr.length == 0) {
            return -1;
        }
        int minVal = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < minVal) {
                minVal = arr[i];
            }
        }
        return minVal;
    }
}
