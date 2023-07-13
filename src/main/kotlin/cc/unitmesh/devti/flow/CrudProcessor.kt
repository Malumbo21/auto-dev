package cc.unitmesh.devti.flow

import cc.unitmesh.devti.analysis.DtClass

interface CrudProcessor {
    fun controllerList(): List<DtClass>
    fun serviceList(): List<DtClass>
    fun modelList(): List<DtClass>
    fun createControllerOrUpdateMethod(targetController: String, code: String, isControllerExist: Boolean)
    fun createController(endpoint: String, code: String): DtClass?
    fun createEntity(code: String): DtClass?
    fun createService(code: String): DtClass?
    fun createDto(code: String): DtClass?
    fun createClass(code: String, packageName: String?): DtClass?

    fun isController(code: String): Boolean {
        if (code.contains("@Controller")) {
            return true
        }

        if (code.contains("import org.springframework.stereotype.Controller")) {
            return true
        }

        // regex to match `public class xxController`
        val regex = Regex("public\\s+class\\s+\\w+Controller")
        return regex.containsMatchIn(code)
    }

    fun isService(code: String): Boolean {
        if (code.contains("@Service")) {
            return true
        }

        if (code.contains("import org.springframework.stereotype.Service")) {
            return true
        }

        // regex to match `public class xxService`
        val regex = Regex("public\\s+class\\s+\\w+Service")
        return regex.containsMatchIn(code)
    }

    fun isEntity(code: String): Boolean {
        if (code.contains("@Entity")) {
            return true
        }

        if (code.contains("import javax.persistence.Entity")) {
            return true
        }

        // regex to match `public class xxEntity`
        val regex = Regex("public\\s+class\\s+\\w+Entity")
        return regex.containsMatchIn(code)
    }

    fun isDto(code: String): Boolean {
        if (code.contains("import lombok.Data")) {
            return true
        }

        // regex to match `public class xxDto`
        val regex = Regex("public\\s+class\\s+\\w+(Dto|DTO|Request|Response|Res|Req)")
        return regex.containsMatchIn(code)
    }

}
