package org.naviqore.app.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final String name;
    private final String version;
    private final String description;

    @Autowired
    public HomeController(BuildProperties buildProperties) {
        name = buildProperties.getName();
        version = buildProperties.getVersion();
        description = buildProperties.get("description");
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("appName", name);
        model.addAttribute("appVersion", version);
        model.addAttribute("appDescription", description.substring(0, description.length() - 1));
        return "index";
    }
}
