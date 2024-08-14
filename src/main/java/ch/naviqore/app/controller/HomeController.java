package ch.naviqore.app.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final String appName;
    private final String appVersion;
    private final String appDescription;

    public HomeController(BuildProperties buildProperties, GitProperties gitProperties,
                          @Value("${application.description:Public Transit Routing System}") String appDescription) {
        this.appName = buildProperties.getName() != null ? buildProperties.getName() : "Naviqore Public Transit API";
        this.appVersion = buildProperties.getVersion() != null ? buildProperties.getVersion() : gitProperties.get(
                "build.version") != null ? gitProperties.get("build.version") : "1.0.0";
        this.appDescription = appDescription;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("appVersion", appVersion);
        model.addAttribute("appDescription", appDescription);
        return "index";
    }
}
