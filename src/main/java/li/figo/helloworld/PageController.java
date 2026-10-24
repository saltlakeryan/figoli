package li.figo.helloworld;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
class PageController {
    @GetMapping("/login")
    ModelAndView login() { return new ModelAndView("forward:/login.html"); }

    @GetMapping("/register")
    ModelAndView register() { return new ModelAndView("forward:/register.html"); }

    @GetMapping({"/admin", "/admin/"})
    ModelAndView admin() { return new ModelAndView("forward:/admin.html"); }

    @GetMapping({"/market", "/market/", "/app1", "/app1/"})
    ModelAndView marketDay() { return new ModelAndView("forward:/market/index.html"); }

    @GetMapping("/market/shopping")
    ModelAndView shoppingMode() { return new ModelAndView("forward:/market/shopping.html"); }
}
