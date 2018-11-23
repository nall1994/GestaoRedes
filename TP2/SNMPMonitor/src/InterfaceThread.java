public class InterfaceThread extends Thread {
    private int ifIndex;
    private int polling_time;

    public InterfaceThread(int polling_time,int ifIndex) {
        this.polling_time = polling_time;
        this.ifIndex = ifIndex;
    }

    public void run() {

    }
}
