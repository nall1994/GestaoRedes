public class Agente {
    private String ip;
    private String porta;
    private int number_interfaces;

    public Agente(String ip, String porta, int number_interfaces) {
        this.ip = ip;
        this.porta = porta;
        this.number_interfaces = number_interfaces;
    }

    public String getIp() {
        return ip;
    }

    public String getPorta() {
        return porta;
    }

    public int getNumber_interfaces() {
        return number_interfaces;
    }

    public void setNumber_interfaces(int number_interfaces) {
        this.number_interfaces = number_interfaces;
    }
}


