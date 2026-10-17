package com.ithopeschool.projectbe.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 모든 API 의 에러를 한 곳에서 받아, docs/API.md 3장의 에러 모양으로 바꿔 돌려주는 곳입니다.
//   { "error": { "code": "NOT_FOUND", "message": "계획을 찾을 수 없어요" } }
//
// Controller·Service 에서는 에러 응답을 직접 만들지 않습니다. 예외를 던지기만 하면 여기로 옵니다.
// (@RestControllerAdvice 는 "모든 Controller 에서 난 예외를 여기서 대신 처리한다" 는 표시입니다)
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  // 1) 우리가 일부러 던진 에러입니다. 예: throw new ApiException(ErrorCode.NOT_FOUND, "계획을 찾을 수 없어요")
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
    return respond(exception.getErrorCode(), exception.getMessage());
  }

  // 2) 요청 DTO 의 @Valid 검사에 걸린 에러입니다. (@NotBlank 인데 빈 값이 온 경우 등)
  // message 에는 검사 어노테이션에 적어 둔 문장이 들어갑니다. 예: @NotBlank(message = "이름을 입력해 주세요")
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidBody(
      MethodArgumentNotValidException exception) {
    // 여러 칸이 틀렸어도 하나만 알려 줍니다. FE 는 문장 하나를 보여 주기 때문입니다.
    FieldError fieldError = exception.getBindingResult().getFieldError();
    if (fieldError == null || fieldError.getDefaultMessage() == null) {
      return respond(ErrorCode.VALIDATION_ERROR);
    }
    return respond(ErrorCode.VALIDATION_ERROR, fieldError.getDefaultMessage());
  }

  // 3) 보낸 값을 읽을 수 없는 에러입니다.
  // JSON 모양이 깨졌거나, 숫자·날짜 자리에 다른 글자가 온 경우입니다. (예: /plans/abc)
  @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class})
  public ResponseEntity<ApiErrorResponse> handleUnreadableValue(Exception exception) {
    return respond(ErrorCode.VALIDATION_ERROR);
  }

  // 4) 위에서 받지 않은 나머지 모든 에러입니다.
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleOthers(Exception exception) {
    // Spring 이 "이건 몇 번 상태" 인지 이미 아는 에러입니다. (없는 주소 404, 틀린 요청 방법 405 등)
    // 상태 번호는 그대로 쓰고, 본문만 우리 모양으로 바꿉니다.
    if (exception instanceof ErrorResponse knownError) {
      HttpStatusCode status = knownError.getStatusCode();
      ErrorCode code = ErrorCode.fromStatus(status.value());
      return ResponseEntity.status(status)
          .headers(knownError.getHeaders())
          .body(ApiErrorResponse.of(code, code.getDefaultMessage()));
    }

    // 예상 못 한 에러입니다. 원인은 서버 로그에만 남기고, 응답에는 내부 정보를 넣지 않습니다.
    log.error("처리하지 못한 에러가 났습니다", exception);
    return respond(ErrorCode.INTERNAL_ERROR);
  }

  // 에러 종류의 기본 문장으로 응답을 만듭니다.
  private ResponseEntity<ApiErrorResponse> respond(ErrorCode code) {
    return respond(code, code.getDefaultMessage());
  }

  // 에러 종류에 맞는 상태 번호와 에러 모양으로 응답을 만듭니다.
  private ResponseEntity<ApiErrorResponse> respond(ErrorCode code, String message) {
    return ResponseEntity.status(code.getStatus()).body(ApiErrorResponse.of(code, message));
  }
}
