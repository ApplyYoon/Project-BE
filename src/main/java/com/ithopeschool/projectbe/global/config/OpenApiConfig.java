package com.ithopeschool.projectbe.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger 설정입니다. Swagger 는 우리가 만든 API 를 브라우저에서 직접 불러 볼 수 있는 화면입니다.
//   http://localhost:8080/swagger-ui.html
@Configuration
public class OpenApiConfig {

  // Swagger 화면 오른쪽 위 "Authorize" 버튼에 보이는 이름입니다.
  private static final String TOKEN_NAME = "로그인 토큰";

  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Project BE API")
                .description("API 를 펼치고 Try it out → Execute 를 누르면 실제로 불러 볼 수 있어요.")
                .version("v1"))
        // "Authorize" 버튼을 만듭니다. 여기에 넣은 토큰은 Authorization 헤더에 담겨 요청과 같이 나갑니다.
        .components(
            new Components()
                .addSecuritySchemes(
                    TOKEN_NAME,
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")))
        .addSecurityItem(new SecurityRequirement().addList(TOKEN_NAME));
  }
}
