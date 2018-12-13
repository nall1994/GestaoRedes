public class InterfaceThread extends Thread {
    private int ifIndex;
    private String type_of_poll;
    private int polling_time;

    public InterfaceThread(int ifIndex,String type_of_poll) {
        if(!type_of_poll.equals("dynamic")){
            this.polling_time = Integer.parseInt(type_of_poll);
            this.type_of_poll = "fixed";
        }
        else {
            this.polling_time = 3000;
            this.type_of_poll = "dynamic";
        }
        this.ifIndex = ifIndex;
    }

    public void run() {
        //while(true) do poll based on polling_time. If it is -1 that means it is a dynamic poll, or else it is time fixed.
    }
}
