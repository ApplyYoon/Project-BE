package com.ithopeschool.projectbe.global.auth;

import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 내 컴퓨터(local)에서만: 서버가 켜질 때 테스트 사용자를 한 명 만들어 둡니다.
// 테스트 토큰(application-local.yml 의 app.auth.test-token)으로 부르면 이 사용자로 로그인한 것으로 봅니다.
//
// 로컬 DB(H2)는 켤 때마다 비어 있어서, 맨 처음 만드는 이 사용자가 항상 1번(CurrentUserProvider.TEST_USER_ID)이 됩니다.
// 그래서 로컬에서 이메일로 가입한 사람은 2번부터 시작합니다.
//
// @Profile("local"): 배포 서버(prod)에서는 이 클래스가 아예 만들어지지 않습니다. 테스트 사용자도 생기지 않습니다.
// ApplicationRunner: 서버가 다 켜진 직후에 run() 을 한 번 실행해 줍니다.
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalTestUserInitializer implements ApplicationRunner {

  private final UserRepository userRepository;

  @Override
  public void run(ApplicationArguments args) {
    // 사용자가 한 명도 없을 때만 만듭니다. (이미 있으면 또 만들지 않습니다)
    if (userRepository.count() == 0) {
      userRepository.save(new User("테스트 사용자"));
    }
  }
}
