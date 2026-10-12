package li.figo.helloworld;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.Arrays;

@SpringBootApplication
@EntityScan(basePackageClasses = HelloWorldApplication.class)
public class HelloWorldApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context=SpringApplication.run(HelloWorldApplication.class, args);
        if (Arrays.stream(args).anyMatch(arg -> arg.startsWith("--figo.admin-command="))) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
