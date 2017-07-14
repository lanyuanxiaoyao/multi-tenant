package cloud;

import cloud.dao.StudentDao;
import cloud.entity.Student;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CloudApplicationTests {

	@Autowired
	private StudentDao studentDao;

	@Test
	public void contextLoads() {

		studentDao.save(new Student(1,"zhangsan"));
		studentDao.save(new Student(2,"lisi"));

		List<Student> studentList = studentDao.findAll();
		System.out.println(studentList.size());

	}

}
