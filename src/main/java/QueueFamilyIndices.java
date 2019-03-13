public class QueueFamilyIndices {

    private int index = -1;

    public boolean isComplete() {
        return index != -1;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}
