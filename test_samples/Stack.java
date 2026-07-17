public class Stack {
    private int[] data;
    private int top;

    public Stack(int capacity) {
        data = new int[capacity];
        top = -1;
    }

    public void push(int value) {
        if (top == data.length - 1) {
            throw new RuntimeException("Stack overflow");
        }
        data[++top] = value;
    }

    public int pop() {
        if (top == -1) {
            throw new RuntimeException("Stack underflow");
        }
        return data[top--];
    }

    public boolean isEmpty() {
        return top == -1;
    }
}
