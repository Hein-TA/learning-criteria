package com.jdc.onestop.criteria;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.jdc.onestop.criteria.input.AppointmentEditForm;
import com.jdc.onestop.criteria.input.AppointmentSearch;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

@TestMethodOrder(OrderAnnotation.class)
public class AppointmentServiceTest {

	private static EntityManagerFactory EMF;
	private AppointmentService service;

	@Order(1)
	@ParameterizedTest
	@CsvSource({ 
		"4,1900-01-01,16:00,1,Test Reason,1", 
		"4,1900-01-01,16:00,2,Test Reason,2",
		"4,1900-01-01,16:00,3,Test Reason,3", 
		"4,1900-01-01,16:00,4,Test Reason,4",
		"4,1900-01-01,16:00,5,Test Reason,5", 
		"3,1900-01-01,16:00,1,Test Reason,1"
		})
	void create_appointment(int doctorId, LocalDate date, String startTime, int patientId, String reason, int seqNumber) {
		assertDoesNotThrow(() -> {
			var result = service.create(new AppointmentEditForm(doctorId, LocalDate.of(1900, 1, 1).equals(date) ? LocalDate.now() : date, startTime, patientId, reason));
			assertEquals(seqNumber, result.seqNumber());
		});
	}

	@Order(2)
	@ParameterizedTest
	@CsvSource({ 
		",,,,,,,6", 
		"ohnmar,,,,,,,5", 
		"Nilar Aung,,,,,,,1", 
		",Kyaw Khant Zin,,,,,,2", 
		",pyae,,,,,,1",
		"aung,,,,,,,0", 
		",,0911223344,,,,,2",
		",,09112233,,,,,6",
		",,,16:00,,,,6",
		",,,17:00,,,,0",
		",,,,,,false,6",
		",,,,,,true,0"
		})
	void test_search_with_random_params(String doctorName, String patientName, String patientPhone, String startTime, LocalDate from, LocalDate to, Boolean canceled, int result) {
		var list = service.search(new AppointmentSearch(doctorName, patientName, patientPhone, startTime, from, to, canceled));
		assertEquals(result, list.size());
	}

	@Order(3)
	@ParameterizedTest
	@CsvSource({ 
		"ohn,Kyaw,091122334,16:00,false,1",
		"nilar,Kyaw,091122334,16:00,false,1",
		"Aung,Zin,0911223344,16:00,false,0",
		"Aung,Chirs,0911223344,16:00,true,0" 
		})
	void test_search_with_full_params(String doctorName, String patientName, String patientPhone, String startTime, Boolean canceled, int result) {
		var list = service.search(new AppointmentSearch(doctorName, patientName, patientPhone, startTime, LocalDate.now(), LocalDate.now(), canceled));
		assertEquals(result, list.size());
	}

	@Order(4)
	@ParameterizedTest
	@CsvSource({ "ohn,Kyaw,091122334,16:00,false,1" })
	void test_search_from_date_with_lesser_than_today(String doctorName, String patientName, String patientPhone, String startTime, Boolean canceled, int result) {
		var list = service.search(new AppointmentSearch(doctorName, patientName, patientPhone, startTime, LocalDate.now().minusDays(10), null, canceled));
		assertEquals(result, list.size());
	}

	@Order(5)
	@ParameterizedTest
	@CsvSource({ "nilar,Kyaw,09112233,16:00,false,1" })
	void test_search_to_date_with_greater_than_today_date(String doctorName, String patientName, String patientPhone, String startTime, Boolean canceled, int result) {
		var list = service.search(new AppointmentSearch(doctorName, patientName, patientPhone, startTime, null, LocalDate.now().plusDays(20), canceled));
		assertEquals(result, list.size());
	}

	@Order(6)
	@ParameterizedTest
	@CsvSource({ "ohn,Kyaw,091122334,16:00,false,0" })
	void test_search_from_date_with_greater_than_today(String doctorName, String patientName, String patientPhone, String startTime, Boolean canceled, int result) {
		var list = service.search(new AppointmentSearch(doctorName, patientName, patientPhone, startTime, LocalDate.now().plusDays(3), null, canceled));
		assertEquals(result, list.size());
	}

	@BeforeAll
	static void start() {
		EMF = Persistence.createEntityManagerFactory("criteria-core-domain");
	}

	@BeforeEach
	void init() {
		service = new AppointmentServiceImpl(EMF::createEntityManager);
	}

	@AfterAll
	static void end() {
		EMF.close();
	}
}
