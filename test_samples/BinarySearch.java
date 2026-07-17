public class BinarySearch {
    public static int search(int[] sortedArr, int target) {
        int low = 0;
        int high = sortedArr.length - 1;
        while (low <= high) {
            int mid = (low + high) / 2;
            if (sortedArr[mid] == target) {
                return mid;
            } else if (sortedArr[mid] < target) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -1;
    }
}
