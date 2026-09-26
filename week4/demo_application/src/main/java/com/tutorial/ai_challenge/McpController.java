package com.tutorial.ai_challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class McpController {

	private static final String MOCK_SERVER = "mock-api";

	private final List<McpSyncClient> clients;

	public McpController(List<McpSyncClient> clients) {
		this.clients = clients;
	}

	public record ToolRow(String name, String description) {
	}

	public record ServerRow(String server, boolean connected, String error, List<ToolRow> tools) {
	}

	@GetMapping("/mcp")
	public String page() {
		return "mcp";
	}

	@PostMapping("/mcp/tools")
	public String tools(Model model) {
		List<ServerRow> servers = new ArrayList<>();
		for (McpSyncClient client : clients) {
			try {
				McpSchema.InitializeResult init = initialize(client);
				McpSchema.Implementation serverInfo = init.serverInfo();
				String serverName = serverInfo != null ? serverInfo.name() : "неизвестный сервер";
				List<ToolRow> tools = client.listTools().tools().stream()
						.map(t -> new ToolRow(t.name(), t.description()))
						.toList();
				servers.add(new ServerRow(serverName, true, null, tools));
			}
			catch (Exception e) {
				servers.add(new ServerRow(client.getClientInfo().name(), false, describe(e), List.of()));
			}
		}
		model.addAttribute("servers", servers);
		return "mcp";
	}

	@PostMapping("/mcp/call")
	public String call(Model model) {
		try {
			McpSyncClient mock = findClient(MOCK_SERVER);
			String color = callTool(mock, "getColor");
			String number = callTool(mock, "getNumber");
			model.addAttribute("callColor", color);
			model.addAttribute("callNumber", number);
			model.addAttribute("callPhrase",
					"Используем результат: цвет дня — " + color + ", число дня — " + number + ".");
		}
		catch (Exception e) {
			model.addAttribute("callError", describe(e));
		}
		return "mcp";
	}

	private McpSyncClient findClient(String serverName) throws IllegalStateException {
		for (McpSyncClient client : clients) {
			try {
				McpSchema.InitializeResult init = initialize(client);
				if (init.serverInfo() != null && serverName.equals(init.serverInfo().name())) {
					return client;
				}
			}
			catch (Exception e) {
				throw new IllegalStateException("Не удалось подключиться к серверу '" + serverName + "': " + describe(e));
			}
		}
		throw new IllegalStateException("Сервер '" + serverName + "' не настроен");
	}

	private McpSchema.InitializeResult initialize(McpSyncClient client) {
		if (!client.isInitialized()) {
			client.initialize();
		}
		return client.getCurrentInitializationResult();
	}

	private String callTool(McpSyncClient client, String toolName) throws IllegalStateException {
		McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(toolName, Map.of()));
		if (Boolean.TRUE.equals(result.isError())) {
			throw new IllegalStateException("Инструмент '" + toolName + "' вернул ошибку");
		}
		StringBuilder text = new StringBuilder();
		for (McpSchema.Content c : result.content()) {
			if (c instanceof McpSchema.TextContent t) {
				if (text.length() > 0) {
					text.append(" ");
				}
				text.append(t.text());
			}
		}
		return text.toString();
	}

	private String describe(Throwable e) {
		StringBuilder msg = new StringBuilder();
		for (Throwable c = e; c != null; c = c.getCause()) {
			if (msg.length() > 0) {
				msg.append(" ← ");
			}
			msg.append(c.getClass().getSimpleName()).append(": ")
					.append(c.getMessage() != null ? c.getMessage() : c.toString());
		}
		return msg.toString();
	}

}
