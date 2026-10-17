package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.cardresult.dto.CardDayResultResponse;
import com.ithopeschool.projectbe.domain.cardresult.dto.PlanPatternResponse;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.web.bind.annotation.RestController;

// ⚠️ 의지력 숫자가 어떤 API 응답에도 들어가지 않는지 지킵니다. (이슈 #231 완료 조건, 최종 기획서 「5.3」 「4.6」)
// "의지력 42점" 같은 점수는 낙인(6번 오류)이 되기 때문입니다. 의지력은 관리 단계를 정하는 내부 값입니다.
//
// 우리 코드의 모든 클래스를 훑어서
//   1. 응답 모양(이름이 Response 로 끝나거나 dto 폴더에 있는 것)에 willpower 라는 칸이 없는지
//   2. Controller 가 돌려주는 값에 의지력 계산기(Willpower)의 숫자 타입이 없는지
// 를 봅니다. 새 응답 DTO 를 만들면 저절로 검사 대상에 들어갑니다.
class WillpowerNotInResponseTest {

  private static final String BASE_PACKAGE = "com.ithopeschool.projectbe";

  // BASE_PACKAGE 안의 모든 클래스(안에 든 클래스 포함)를 찾습니다.
  // Spring 의 "클래스 찾기 도구" 는 원래 @Component 만 찾아서, 모든 클래스를 받도록 두 곳을 바꿔 줍니다.
  private static List<Class<?>> allClasses() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false) {
          // 인터페이스 · 안에 든 클래스도 빼지 않습니다.
          @Override
          protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            return true;
          }
        };
    // 어떤 클래스든 통과시킵니다.
    scanner.addIncludeFilter((metadataReader, metadataReaderFactory) -> true);

    List<Class<?>> classes = new ArrayList<>();
    for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
      classes.add(Class.forName(definition.getBeanClassName()));
    }
    return classes;
  }

  // 응답 모양으로 보는 클래스: dto 폴더에 있거나 이름이 Response 로 끝나는 것
  private static boolean isResponseShape(Class<?> type) {
    return type.getPackageName().endsWith(".dto") || type.getSimpleName().endsWith("Response");
  }

  @Test
  @DisplayName("어떤 응답 DTO 에도 willpower 칸이 없다 (이번에 만든 카드 결과 응답 포함)")
  void noWillpowerFieldInResponses() throws ClassNotFoundException {
    List<Class<?>> responseShapes = new ArrayList<>();
    for (Class<?> type : allClasses()) {
      if (isResponseShape(type)) {
        responseShapes.add(type);
      }
    }
    // 찾기가 제대로 됐는지: 응답 DTO 가 많이 있고, 이번에 만든 두 응답도 들어 있어야 합니다.
    assertThat(responseShapes.size()).isGreaterThan(30);
    assertThat(responseShapes).contains(CardDayResultResponse.class, PlanPatternResponse.class);

    for (Class<?> type : responseShapes) {
      for (Field field : type.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        assertThat(field.getName().toLowerCase(Locale.ROOT))
            .as("%s 의 칸 %s", type.getName(), field.getName())
            .doesNotContain("willpower");
      }
    }
  }

  @Test
  @DisplayName("Controller 가 돌려주는 값에 의지력 계산기의 타입이 없다")
  void noControllerReturnsWillpower() throws ClassNotFoundException {
    int controllerCount = 0;
    for (Class<?> type : allClasses()) {
      if (!type.isAnnotationPresent(RestController.class)) {
        continue;
      }
      controllerCount++;
      // 의지력 서비스는 Controller 가 쓰면 안 됩니다. (관리 단계 판정만 씀)
      for (Field field : type.getDeclaredFields()) {
        assertThat(field.getType())
            .as("%s 의 칸 %s", type.getName(), field.getName())
            .isNotEqualTo(WillpowerService.class);
      }
      for (Method method : type.getDeclaredMethods()) {
        assertThat(method.getGenericReturnType().getTypeName())
            .as("%s.%s 가 돌려주는 값", type.getName(), method.getName())
            .doesNotContain(Willpower.class.getName());
      }
    }
    assertThat(controllerCount).isGreaterThan(10);
  }
}
