package domox.webapp.application;

import domox.dom.Analysis;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {"domox.dom"})
@EntityScan(basePackageClasses = {Analysis.class})
public class ApplicationModule {
}
