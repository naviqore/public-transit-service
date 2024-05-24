package ch.naviqore.api.controller;

import ch.naviqore.api.model.Autocomplete;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/schedule")
public class ScheduleController {

    @GetMapping("/stops/autocomplete")
    public List<Autocomplete> getInquiries(@RequestParam String query) {
        List<Autocomplete> dtos = new ArrayList<>();
        Autocomplete dto = new Autocomplete();
        dto.setId("1");
        dto.setName("Autocomplete 1");
        dtos.add(dto);
        dto = new Autocomplete();
        dto.setId("2");
        dto.setName("Autocomplete 2");
        dtos.add(dto);
        return dtos;
    }

}
