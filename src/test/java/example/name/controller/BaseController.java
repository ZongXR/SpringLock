package example.name.controller;


import example.name.service.BaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class BaseController {

    @Autowired
    private BaseService baseService = null;

    @RequestMapping(value = "/testSynchronized", method = RequestMethod.GET)
    @ResponseBody
    public String testSynchronized(){
        String result = this.baseService.testSynchronized();
        return result;
    }

    @RequestMapping(value = "/testReadLock", method = RequestMethod.GET)
    @ResponseBody
    public String testReadLock(){
        String result = this.baseService.testReadLock();
        return result;
    }

    @RequestMapping(value = "/testWriteLock", method = RequestMethod.GET)
    @ResponseBody
    public String testWriteLock(){
        String result = this.baseService.testWriteLock();
        return result;
    }

    @RequestMapping(value = "/testOptimisticLock", method = RequestMethod.GET)
    @ResponseBody
    public String testOptimisticLock(){
        String result = this.baseService.testOptimisticLock();
        return result;
    }
}
