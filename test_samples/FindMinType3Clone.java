public class FindMinType3Clone {
    public static int find(int[] arr) {
        int minVal;
        boolean isEmpty = (arr == null || arr.length == 0);
        if (isEmpty) {
            return -1;
        }
        minVal = arr[0];
        int i = 1;
        while (i < arr.length) {
            int current = arr[i];
            if (current < minVal) {
                minVal = current;
            }
            i++;
        }
        System.out.println("Minimum computed.");
        return minVal;
    }
}
