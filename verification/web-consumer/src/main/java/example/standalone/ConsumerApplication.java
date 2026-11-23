package example.standalone;

import com.notshahrad.spring_boot_base.common.exception.ResourceNotFoundException;
import com.notshahrad.spring_boot_base.common.response.ApiResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }

    @GetMapping("/example")
    ApiResponse<String> example() {
        return ApiResponse.success("standalone");
    }

    @GetMapping("/missing")
    void missing() {
        throw new ResourceNotFoundException("Example", 42);
    }
}
