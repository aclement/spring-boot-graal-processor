package com.example.demo2;

import org.springframework.CompilationHint;

@CompilationHint(member={"foo"})
public @interface Sample2 {
    
    String[] foo() default {};

}