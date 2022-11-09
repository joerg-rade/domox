package domox.ruleengine;

import org.springframework.stereotype.Component;

@Component
public class HelloWorldComponent {
    public String getHelloWorld(String hello, String world) {
        return hello + " " + world + "!";
    }
}