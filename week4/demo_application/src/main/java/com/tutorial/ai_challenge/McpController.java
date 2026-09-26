package com.tutorial.ai_challenge;

import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class McpController {

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
				if (!client.isInitialized()) {
					client.initialize();
				}
				McpSchema.InitializeResult init = client.getCurrentInitializationResult();
				McpSchema.Implementation serverInfo = init != null ? init.serverInfo() : null;
				String serverName = serverInfo != null ? serverInfo.name() : "неизвестный сервер";
				List<ToolRow> tools = client.listTools().tools().stream()
						.map(t -> new ToolRow(t.name(), t.description()))
						.toList();
				servers.add(new ServerRow(serverName, true, null, tools));
			}
			catch (Exception e) {
				StringBuilder msg = new StringBuilder();
				for (Throwable c = e; c != null; c = c.getCause()) {
					if (msg.length() > 0) {
						msg.append(" ← ");
					}
					msg.append(c.getClass().getSimpleName()).append(": ")
							.append(c.getMessage() != null ? c.getMessage() : c.toString());
				}
				servers.add(new ServerRow(client.getClientInfo().name(), false, msg.toString(), List.of()));
			}
		}
		model.addAttribute("servers", servers);
		return "mcp";
	}

}
