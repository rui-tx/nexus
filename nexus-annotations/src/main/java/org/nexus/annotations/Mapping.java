package org.nexus.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.nexus.commons.enums.HttpMethod;
import org.nexus.commons.enums.ResponseType;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Mapping {

  HttpMethod type();

  String endpoint();

  ResponseType responseType() default ResponseType.JSON;
}
