import org.snmp4j.smi.OID;

import java.io.IOException;
import java.lang.Thread;
import java.util.Scanner;

public class ServerTester extends Thread {
    private SNMPAgentFunctions agent_functions;
    private int numberImages;
    private SNMPManager client;

    public ServerTester(SNMPAgentFunctions agent_functions,int numberImages) {
        this.agent_functions = agent_functions;
        this.numberImages = numberImages;
        this.client = agent_functions.getClient();
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        boolean terminate = false;
        while(!terminate) {
            printMenu();
            int escolha = scanner.nextInt();
            String result = "";
            switch(escolha) {
                case 1:
                    //Pedir carregamento de parâmetros
                    printContainersMenu();
                    escolha = scanner.nextInt();
                    result = agent_functions.loadParameters(escolha);
                    System.out.println(result);
                    break;
                case 2:
                    //Pedir criação de container
                    result = agent_functions.create();
                    System.out.println("Identificador do container criado: " + result);
                    break;
                case 3:
                    //listar containers
                    result = agent_functions.listContainers();
                    System.out.println(result);
                    break;
                case 4:
                    //remover container
                    String createdContainers = agent_functions.getCreatedContainers();
                    printCreatedContainersMenu(createdContainers);
                    escolha = scanner.nextInt();
                    result = agent_functions.removeContainer(escolha);
                    System.out.println(result);
                case 5:
                    terminate = true;
                    break;
                default:
                    System.out.println("Escolha não reconhecida!");
            }
        }
        System.exit(0);

    }

    private void printMenu() {
        System.out.println("-----CONTAINERS MENU-----");
        System.out.println("Escolha a sua opção (1,2 ou 3):");
        System.out.println("1 - Carregar parâmetros de um container.");
        System.out.println("2 - Criar o container carregado.");
        System.out.println("3 - Listar containers criados.");
        System.out.println("4 - Desligar e remover um container.");
        System.out.println("5 - Terminar aplicação gestora.");
    }

    private void printContainersMenu() {
        try {
            System.out.println("Escolha o container do qual quer carregar os parâmetros para criação:");
            for(int i = 1; i <= numberImages;i++) {
                System.out.println(i + " - " + client.getAsString(new OID("1.3.6.1.3.2019.2.1.1." + i)));
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private void printCreatedContainersMenu(String createdContainers) {
        String[] perContainer = createdContainers.split("\n");
        System.out.println("Escolha o container que quer remover:");
        for(int i = 0; i < perContainer.length; i++) {
            System.out.println(i+1 + " - " + perContainer[i]);
        }
    }
}
