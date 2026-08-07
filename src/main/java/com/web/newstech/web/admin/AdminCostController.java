package com.web.newstech.web.admin;

import com.web.newstech.shared.config.NewsTechProperties;
import com.web.newstech.shared.cost.CostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AdminCostController {

	private final CostService costService;
	private final NewsTechProperties properties;

	@GetMapping("/admin/custo")
	public String custo(@RequestParam(defaultValue = "30") int dias, Model model) {
		model.addAttribute("report", costService.report(Math.clamp(dias, 1, 365)));
		model.addAttribute("dias", dias);
		// Estado dos agendadores no topo: se o painel mostra zero, a primeira pergunta
		// e sempre se o pipeline chegou a rodar.
		model.addAttribute("autoTriage", properties.claude().autoTriage());
		model.addAttribute("autoEditorial", properties.claude().autoEditorial());
		return "admin/custo";
	}

}
