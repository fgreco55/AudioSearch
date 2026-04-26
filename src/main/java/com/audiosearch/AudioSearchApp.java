package com.audiosearch;

import com.audiosearch.commands.AppState;
import com.audiosearch.commands.CommandHandler;

import java.io.IOException;
import java.util.Scanner;

public class AudioSearchApp {
    private static final AppState appState = new AppState();
    private static final CommandHandler commandHandler = new CommandHandler(appState);

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Error: OPENAI_API_KEY environment variable not set");
            System.exit(1);
        }

        Scanner scanner = new Scanner(System.in);
        System.out.println("AudioSearch CLI - Type /help for available commands");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            if (!input.startsWith("/")) {
                System.err.println("Commands must start with '/'");
                continue;
            }

            try {
                handleCommand(input, apiKey, scanner);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void handleCommand(String input, String apiKey, Scanner scanner) throws IOException {
        String[] parts = input.split("\\s+", 2);
        String command = parts[0].substring(1).toLowerCase();
        String argument = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "index":
                commandHandler.handleIndexCommand(apiKey, scanner, argument);
                break;
            case "file":
                commandHandler.handleFileCommand(apiKey, scanner);
                break;
            case "timestamps":
                commandHandler.handleTimestampsCommand();
                break;
            case "search":
                commandHandler.handleSearchCommand(apiKey, scanner, argument);
                break;
            case "threshold":
                commandHandler.handleThresholdCommand(argument);
                break;
            case "status":
                commandHandler.handleStatusCommand();
                break;
            case "deduplicate":
                commandHandler.handleDeduplicateCommand();
                break;
            case "help":
                commandHandler.printHelp();
                break;
            case "exit":
            case "quit":
                System.out.println("Goodbye!");
                System.exit(0);
                break;
            default:
                System.err.println("Unknown command: /" + command);
                System.out.println("Type /help for available commands");
        }
    }

}
