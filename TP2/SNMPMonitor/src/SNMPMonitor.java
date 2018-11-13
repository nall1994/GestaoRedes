import java.io.*;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

public class SNMPMonitor {
    private static String path_to_database = "../Database/database.json";
    private static String path_to_config = "monitor.config";
    private static boolean pastMenu = false;

    public static void main(String[] args) {
        Scanner s = new Scanner(System.in);
        int opcao;
        while(true) {
            if(pastMenu) break;
            System.out.println("Escolha a opção associada à acção que pretende realizar: ");
            System.out.println("1 - Executar o monitor.");
            System.out.println("2 - Acrescentar Agente SNMP.");
            System.out.println("3 - Remover Agente SNMP.");
            System.out.println("4 - Listar Agentes SNMP.");
            System.out.println("5 - Terminar Monitor.");
            while(true) {
                try {
                    opcao = s.nextInt();
                    if(opcao > 0 && opcao < 6)
                        break;
                    else System.out.println("Introduza apenas o número 1, 2, 3, 4 ou 5.");
                } catch(InputMismatchException ime) {
                    System.out.println("Introduza um número: 1, 2, 3, 4 ou 5.");
                    s.nextLine();
                }
            }
            s.nextLine();
            String ip,porta;
            Agente agente;
            switch(opcao) {
                case 1:
                    executeAgents();
                    break;
                case 2:
                    System.out.println("Tenha atenção que a correta inserção do ip e da porta do agente é da sua responsabilidade!");
                    System.out.println("Insira o IP do Agente:");
                    ip = s.nextLine();
                    System.out.println("Insira a porta onde o Agente escuta:");
                    porta = s.nextLine();
                    agente = new Agente(ip,porta);
                    addAgentToConfigurationFile(agente);
                    break;
                case 3:
                    System.out.println("Insira o IP do Agente a remover:");
                    ip = s.nextLine();
                    System.out.println("Insira a porta onde o Agente a remover escuta:");
                    porta = s.nextLine();
                    agente = new Agente(ip,porta);
                    removeAgentFromConfigurationFile(agente);
                    break;
                case 4:
                    listAgents();
                    break;
                case 5:
                    System.exit(0);
                default:
                    break;

            }
        }

        while(true);

    }

    private static void executeAgents() {
        pastMenu = true;
        try {
            FileReader fileReader = new FileReader(path_to_config);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line = null;
            List<Agente> agentes = new ArrayList<>();
            while((line = bufferedReader.readLine())!=null) {
                String[] params = line.split(":");
                Agente agente = new Agente(params[0],params[1]);
                agentes.add(agente);
            }
            bufferedReader.close();

            for(Agente a : agentes) {
                System.out.println("Realizando consultas ao agente com ip: " + a.getIp() + " e porta: " + a.getPorta() + "....");
                SingleAgentConsultant sac = new SingleAgentConsultant(a);
                sac.start();
            }
        }catch(FileNotFoundException fnfe) {
            System.out.println("Não foi possível encontrar o ficheiro de configuração!");
        }catch(IOException ioe) {
            System.out.println("Não foi possível ler do ficheiro de configuração!");
        }
    }

    private static void addAgentToConfigurationFile(Agente agente) {
        String line = null;
        boolean exists = false;

        try {
            FileReader fileReader = new FileReader(path_to_config);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            while((line = bufferedReader.readLine())!= null) {
                String[] params = line.split(":");
                if((agente.getIp().equalsIgnoreCase(params[0])) && (agente.getPorta().equalsIgnoreCase(params[1]))) {
                    System.out.println("Esse agente já se encontra registado no ficheiro de configuração!");
                    exists = true;
                }
            }
            bufferedReader.close();

            if(!exists) {
                FileWriter fileWriter = new FileWriter(path_to_config,true);
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                bufferedWriter.write(agente.getIp() + ":" + agente.getPorta());
                bufferedWriter.newLine();
                System.out.println("Agente com ip: " + agente.getIp() + " e porta: " + agente.getPorta() + " acrescentado!");
                System.out.println("\n");
                bufferedWriter.close();
            }

        } catch(FileNotFoundException fnfe) {
            System.out.println("Não foi possível encontrar o ficheiro de configuração!");
        } catch(IOException ioe) {
            System.out.println("Não foi possível continuar a ler/escrever o ficheiro!");
        }

    }

    private static void removeAgentFromConfigurationFile(Agente agente) {
        try {
            FileReader fileReader = new FileReader(path_to_config);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line = null;
            String postWrite = "";
            String[] params;
            while((line = bufferedReader.readLine())!=null) {
                params = line.split(":");
                if(!((agente.getIp().equalsIgnoreCase(params[0])) && (agente.getPorta().equalsIgnoreCase(params[1])))) {
                    postWrite += line + "\n";
                }
            }

            bufferedReader.close();

            FileWriter fileWriter = new FileWriter(path_to_config,false);
            BufferedWriter bufferedWriter = new BufferedWriter((fileWriter));
            bufferedWriter.write(postWrite);
            System.out.println("Agente removido com sucesso!");
            System.out.println("\n");
            bufferedWriter.close();
        }catch(FileNotFoundException fnfe) {
            System.out.println("Não conseguimos encontrar o ficheiro!");
        }catch(IOException ioe) {
            System.out.println("Não foi possível continuar a ler do ficheiro!");
        }

    }

    private static void listAgents() {
        try {
            FileReader fileReader = new FileReader(path_to_config);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            System.out.println("---- Agentes no ficheiro de configuração ----");
            String line = null;
            int i = 1;
            String[] params;
            while((line = bufferedReader.readLine())!= null) {
                params = line.split(":");
                System.out.println("Agente " + i + " -> ip: " + params[0] + "; porta: " + params[1] );
                i++;
            }
            System.out.println("\n");
            bufferedReader.close();
        } catch(FileNotFoundException fnfe) {
            System.out.println("Não foi possível encontrar o ficheiro!");
        } catch(IOException ioe) {
            System.out.println("Não foi possível ler do ficheiro!");
        }



    }
}
