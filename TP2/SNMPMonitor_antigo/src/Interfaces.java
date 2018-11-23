import java.util.HashMap;

public class Interfaces {
    private HashMap<Integer,InterfaceValues> interfacesInfo;

    public Interfaces() {
        this.interfacesInfo = new HashMap<>();
    }

    public HashMap<Integer,InterfaceValues> getInterfacesInfo() {
        return this.interfacesInfo;
    }

    public void addInterfaceInfo(int ifIndex,InterfaceValues iv) {
        this.interfacesInfo.put(ifIndex,iv);
    }
}
